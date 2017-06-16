package io.github.mayhewsw.classifier;

import edu.illinois.cs.cogcomp.lbjava.classify.ScoreSet;
import edu.illinois.cs.cogcomp.lbjava.learn.BatchTrainer;
import io.github.mayhewsw.classifier.lbjava.CandClassifier;

import java.util.ArrayList;

/**
 * Created by mayhew2 on 6/15/17.
 */
public class Trainer {

    public CandClassifier cc;
    public boolean trained = false;


    public Trainer(){
        cc = new CandClassifier();
    }

    /**
     * This will train the NamePairClassifier
     */
    public void trainClassifier(ArrayList<Candidate> candlist){
        int rounds = 10;
        cc = new CandClassifier();

        for(int i = 0; i < rounds; i++){
            for(Candidate c : candlist ){
                cc.learn(c);
            }
            this.cc.doneWithRound();
        }
        this.cc.doneLearning();
        trained = true;
    }

    public double score(Candidate c){
        ScoreSet ss = cc.scores(c);
        return ss.get("true");
    }

}
