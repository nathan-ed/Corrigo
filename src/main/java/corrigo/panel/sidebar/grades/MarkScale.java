/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades;

import java.util.*;

/**
 * How the points of a copy give its mark. Three kinds:
 * <ul>
 *     <li>LINEAR: points / total × (max − min) + min (the Swiss 1–6 scale by default)</li>
 *     <li>TABLE: from a number of points, a mark ("from 18 points: 6")</li>
 *     <li>FORMULA: an expression of p (points) and t (total), e.g. "p / t * 5 + 1"</li>
 * </ul>
 * The mark is then rounded to the step (0.5: to the nearest half) and kept between min and max. No JavaFX here.
 */
public final class MarkScale {

    public enum Kind { LINEAR, TABLE, FORMULA }

    // From this number of points (included), this mark
    public record Threshold(double points, double mark) {}

    // Floating point errors must not change the rounding: 17.5/20*5+1 is 5.375 and must give 5.5.
    private static final double EPSILON = 1e-9;

    public static final MarkScale SWISS = new MarkScale(Kind.LINEAR, 1, 6, .5, List.of(), "p / t * 5 + 1");

    private final Kind kind;
    private final double min, max, step;
    private final List<Threshold> table; // Sorted by points
    private final String formula;
    private final Formula parsed; // Null if the formula is not valid

    public MarkScale(Kind kind, double min, double max, double step, List<Threshold> table, String formula){
        this.kind = kind == null ? Kind.LINEAR : kind;
        this.min = min;
        this.max = Math.max(min, max);
        this.step = step;
        ArrayList<Threshold> sorted = new ArrayList<>(table == null ? List.of() : table);
        sorted.sort(Comparator.comparingDouble(Threshold::points));
        this.table = List.copyOf(sorted);
        this.formula = formula == null ? "" : formula.strip();
        Formula f;
        try{
            f = Formula.parse(this.formula);
        }catch(IllegalArgumentException e){
            f = null;
        }
        this.parsed = f;
    }

    public Kind getKind(){ return kind; }
    public double getMin(){ return min; }
    public double getMax(){ return max; }
    public double getStep(){ return step; }
    public List<Threshold> getTable(){ return table; }
    public String getFormula(){ return formula; }

    // Why this scale cannot compute marks, or null
    public String getError(){
        if(kind == Kind.FORMULA){
            try{
                Formula.parse(formula);
            }catch(IllegalArgumentException e){
                return e.getMessage();
            }
        }
        if(kind == Kind.TABLE && table.isEmpty()) return "empty table";
        return null;
    }

    public double compute(double points, double total){
        double mark = switch(kind){
            case LINEAR -> total <= 0 ? min : points / total * (max - min) + min;
            case TABLE -> {
                double found = min;
                for(Threshold threshold : table) if(points + EPSILON >= threshold.points()) found = threshold.mark();
                yield found;
            }
            case FORMULA -> parsed == null ? min : parsed.evaluate(points, total);
        };
        if(Double.isNaN(mark) || Double.isInfinite(mark)) mark = min;
        return Math.clamp(round(mark), min, max);
    }

    public double round(double mark){
        if(step <= 0) return mark;
        return Math.floor(mark / step + .5 + EPSILON) * step;
    }

    // The smallest of these raises that changes the mark, if any. A copy can't get more points than the total.
    public OptionalDouble getChangingRaise(double points, double total, double[] raises){
        double mark = compute(points, total);
        for(double raise : raises){
            if(points + raise > total + EPSILON) break;
            if(compute(points + raise, total) > mark + EPSILON) return OptionalDouble.of(raise);
        }
        return OptionalDouble.empty();
    }

    // YAML

    public LinkedHashMap<String, Object> toYAML(){
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("kind", kind.name());
        data.put("min", min);
        data.put("max", max);
        data.put("step", step);
        if(!table.isEmpty()){
            ArrayList<Object> rows = new ArrayList<>();
            for(Threshold threshold : table){
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                row.put("points", threshold.points());
                row.put("mark", threshold.mark());
                rows.add(row);
            }
            data.put("table", rows);
        }
        if(!formula.isEmpty()) data.put("formula", formula);
        return data;
    }

    // The Swiss scale if there is no data
    public static MarkScale fromYAML(Map<?, ?> data){
        if(data == null || data.isEmpty()) return SWISS;
        Kind kind;
        try{
            kind = Kind.valueOf(String.valueOf(data.get("kind")));
        }catch(IllegalArgumentException e){
            kind = Kind.LINEAR;
        }
        ArrayList<Threshold> table = new ArrayList<>();
        if(data.get("table") instanceof List<?> rows){
            for(Object row : rows){
                if(row instanceof Map<?, ?> map && map.get("points") instanceof Number points && map.get("mark") instanceof Number mark){
                    table.add(new Threshold(points.doubleValue(), mark.doubleValue()));
                }
            }
        }
        return new MarkScale(kind, number(data.get("min"), SWISS.min), number(data.get("max"), SWISS.max), number(data.get("step"), SWISS.step),
                table, data.get("formula") == null ? SWISS.formula : String.valueOf(data.get("formula")));
    }
    private static double number(Object value, double fallback){
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    @Override public boolean equals(Object o){
        return o instanceof MarkScale other && toYAML().equals(other.toYAML());
    }
    @Override public int hashCode(){
        return toYAML().hashCode();
    }

    /**
     * An arithmetic expression of p (points) and t (total): + − * / ^, parentheses, numbers ("2.5"),
     * round(x), floor(x), ceil(x), abs(x), min(a, b), max(a, b). "points" and "total" can be written in full.
     */
    public static final class Formula {
        private interface Node { double eval(double p, double t); }
        private final Node root;

        private Formula(Node root){
            this.root = root;
        }
        public double evaluate(double points, double total){
            return root.eval(points, total);
        }

        public static Formula parse(String text){
            if(text == null || text.isBlank()) throw new IllegalArgumentException("empty formula");
            Parser parser = new Parser(text.replace('×', '*').replace('÷', '/').replace('−', '-'));
            Node node = parser.expression();
            parser.skipSpaces();
            if(parser.pos < parser.text.length()) throw new IllegalArgumentException("unexpected \"" + parser.text.charAt(parser.pos) + "\"");
            return new Formula(node);
        }

        private static final class Parser {
            final String text;
            int pos;
            Parser(String text){ this.text = text; }

            void skipSpaces(){
                while(pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
            }
            boolean eat(char c){
                skipSpaces();
                if(pos < text.length() && text.charAt(pos) == c){
                    pos++;
                    return true;
                }
                return false;
            }
            Node expression(){
                Node node = term();
                while(true){
                    if(eat('+')){ Node a = node, b = term(); node = (p, t) -> a.eval(p, t) + b.eval(p, t); }
                    else if(eat('-')){ Node a = node, b = term(); node = (p, t) -> a.eval(p, t) - b.eval(p, t); }
                    else return node;
                }
            }
            Node term(){
                Node node = power();
                while(true){
                    if(eat('*')){ Node a = node, b = power(); node = (p, t) -> a.eval(p, t) * b.eval(p, t); }
                    else if(eat('/')){ Node a = node, b = power(); node = (p, t) -> a.eval(p, t) / b.eval(p, t); }
                    else return node;
                }
            }
            Node power(){
                Node base = unary();
                if(eat('^')){ Node exponent = power(); return (p, t) -> Math.pow(base.eval(p, t), exponent.eval(p, t)); }
                return base;
            }
            Node unary(){
                if(eat('-')){ Node node = unary(); return (p, t) -> -node.eval(p, t); }
                if(eat('+')) return unary();
                return primary();
            }
            Node primary(){
                skipSpaces();
                if(eat('(')){
                    Node node = expression();
                    if(!eat(')')) throw new IllegalArgumentException("missing \")\"");
                    return node;
                }
                if(pos >= text.length()) throw new IllegalArgumentException("incomplete formula");
                char c = text.charAt(pos);
                if(Character.isDigit(c) || c == '.'){
                    int start = pos;
                    while(pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) pos++;
                    try{
                        double value = Double.parseDouble(text.substring(start, pos));
                        return (p, t) -> value;
                    }catch(NumberFormatException e){
                        throw new IllegalArgumentException("wrong number \"" + text.substring(start, pos) + "\"");
                    }
                }
                if(Character.isLetter(c)){
                    int start = pos;
                    while(pos < text.length() && Character.isLetter(text.charAt(pos))) pos++;
                    String name = text.substring(start, pos).toLowerCase(Locale.ROOT);
                    switch(name){
                        case "p", "points": return (p, t) -> p;
                        case "t", "total": return (p, t) -> t;
                    }
                    if(!eat('(')) throw new IllegalArgumentException("unknown name \"" + name + "\" (p: points, t: total)");
                    Node a = expression();
                    Node b = eat(',') ? expression() : null;
                    if(!eat(')')) throw new IllegalArgumentException("missing \")\"");
                    return switch(name){
                        case "round" -> (p, t) -> Math.floor(a.eval(p, t) + .5 + EPSILON);
                        case "floor" -> (p, t) -> Math.floor(a.eval(p, t) + EPSILON);
                        case "ceil" -> (p, t) -> Math.ceil(a.eval(p, t) - EPSILON);
                        case "abs" -> (p, t) -> Math.abs(a.eval(p, t));
                        case "min" -> { if(b == null) throw new IllegalArgumentException("min(a, b)"); yield (p, t) -> Math.min(a.eval(p, t), b.eval(p, t)); }
                        case "max" -> { if(b == null) throw new IllegalArgumentException("max(a, b)"); yield (p, t) -> Math.max(a.eval(p, t), b.eval(p, t)); }
                        default -> throw new IllegalArgumentException("unknown function \"" + name + "\"");
                    };
                }
                throw new IllegalArgumentException("unexpected \"" + c + "\"");
            }
        }
    }
}
