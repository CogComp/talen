package hello;

/**
 * Created by stephen on 5/25/16.
 */

public class Annotation {

    private long id;
    private String text;
    private int startspan;
    private int endspan;
    private String label;


    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getStartspan() {
        return startspan;
    }

    public void setStartspan(int startspan) {
        this.startspan = startspan;
    }

    public int getEndspan() {
        return endspan;
    }

    public void setEndspan(int endspan) {
        this.endspan = endspan;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}