package edu.illinois.cs.cogcomp.lorelei.kb;

import org.mapdb.Serializer;

import java.io.Serializable;

/**
 * Created by mayhew2 on 7/28/17.
 */
public class KBEntry implements Serializable {

    public String asciiname;
    public int id;
    public double score;

    public KBEntry(int id, String asciiname){
        this.id = id;
        this.asciiname = asciiname;
        this.score = 0;
    }

    public KBEntry(int id, String asciiname, double score){
        this.id = id;
        this.asciiname = asciiname;
        this.score = score;
    }

    public void increment(double n){
        this.score += n;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        KBEntry kbEntry = (KBEntry) o;

        if (id != kbEntry.id) return false;
        return asciiname != null ? asciiname.equals(kbEntry.asciiname) : kbEntry.asciiname == null;

    }

    @Override
    public int hashCode() {
        int result = asciiname != null ? asciiname.hashCode() : 0;
        result = 31 * result + id;
        return result;
    }
}
