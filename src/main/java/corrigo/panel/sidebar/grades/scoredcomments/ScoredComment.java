/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.scoredcomments;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// A catalog entry: a comment that adds (points > 0) or removes (points < 0) points to a sub-grade.
public class ScoredComment {

    private final String id;
    private String gradePath;
    private String text;
    private double points;
    // Hex color (#rrggbb), or null to use the default color of the points sign.
    private String color;

    public ScoredComment(String gradePath, String text, double points, String color){
        this(UUID.randomUUID().toString(), gradePath, text, points, color);
    }
    public ScoredComment(String id, String gradePath, String text, double points, String color){
        if(id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank.");
        this.id = id;
        this.gradePath = gradePath == null ? "" : gradePath;
        this.text = text == null ? "" : text;
        this.points = points;
        this.color = color == null || color.isBlank() ? null : color;
    }

    public ScoredComment copyWithNewId(){
        return new ScoredComment(gradePath, text, points, color);
    }
    public ScoredComment copy(){
        return new ScoredComment(id, gradePath, text, points, color);
    }

    public LinkedHashMap<String, Object> toYAML(){
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("gradePath", gradePath);
        data.put("text", text);
        data.put("points", points);
        if(color != null) data.put("color", color);
        return data;
    }
    // Returns null if the data is not a valid entry.
    public static ScoredComment fromYAML(Map<?, ?> data){
        Object id = data.get("id");
        if(id == null || id.toString().isBlank()) return null;
        double points;
        try{
            points = Double.parseDouble(String.valueOf(data.get("points")));
        }catch(NumberFormatException e){
            return null;
        }
        Object gradePath = data.get("gradePath");
        Object text = data.get("text");
        Object color = data.get("color");
        return new ScoredComment(id.toString(), gradePath == null ? "" : gradePath.toString(),
                text == null ? "" : text.toString(), points, color == null ? null : color.toString());
    }

    public String getId(){
        return id;
    }
    public String getGradePath(){
        return gradePath;
    }
    public void setGradePath(String gradePath){
        this.gradePath = gradePath == null ? "" : gradePath;
    }
    public String getText(){
        return text;
    }
    public void setText(String text){
        this.text = text == null ? "" : text;
    }
    public double getPoints(){
        return points;
    }
    public void setPoints(double points){
        this.points = points;
    }
    public String getColor(){
        return color;
    }
    public void setColor(String color){
        this.color = color == null || color.isBlank() ? null : color;
    }
}
