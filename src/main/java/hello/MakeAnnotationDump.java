package hello;

import edu.illinois.cs.cogcomp.annotation.AnnotatorException;
import edu.illinois.cs.cogcomp.annotation.AnnotatorService;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.core.utilities.SerializationHelper;
import edu.illinois.cs.cogcomp.core.utilities.configuration.ResourceManager;
import edu.illinois.cs.cogcomp.nlp.pipeline.IllinoisPipelineFactory;

import java.io.IOException;
import java.util.List;
import java.util.TreeMap;

/**
 * This is just an example of how to get TextAnnotations in a certain folder.
 *
 * Created by mayhew2 on 5/27/16.
 */
public class MakeAnnotationDump {
    public static void main(String[] args) throws IOException, AnnotatorException {

        ResourceManager rm = new ResourceManager( "config/pipeline-config.properties" );
        AnnotatorService pipeline = IllinoisPipelineFactory.buildPipeline( rm );

        List<String> lines = LineIO.read("src/main/resources/eng.txt");

        for(int i = 0; i < lines.size(); i++){
            String text = lines.get(i);
            TextAnnotation ta = pipeline.createAnnotatedTextAnnotation( "nothing", i+"", text );
            SerializationHelper.serializeTextAnnotationToFile(ta, "tas/eng/ta-" + i, true);
        }

    }

}
