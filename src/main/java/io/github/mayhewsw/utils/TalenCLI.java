package io.github.mayhewsw.utils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.core.io.IOUtils;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.core.utilities.SerializationHelper;
import org.apache.commons.cli.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class TalenCLI {

    private static HashMap<String, String> id2html;


    private static HashMap<String, String> labelcolors;

    public static void main(String[] args) throws ParseException, IOException, URISyntaxException {

        Options options = new Options();
        Option help = new Option( "help", "Run a simple TALEN server, just for viewing files." );

        Option indiropt = Option.builder("indir")
                .desc("Folder of json textannotations")
                .hasArg()
                .required()
                .build();

        Option outdiropt = Option.builder("outdir")
                .desc("Folder in which to write HTML files. (default is /tmp/talen/)")
                .hasArg()
                .build();


        Option romanopt = Option.builder("roman")
                .desc("Whether or not to romanize. Default is false.")
                .build();


        Option portopt = Option.builder("port")
                .desc("Which port to use. Default is 8080")
                .hasArg()
                .build();

        options.addOption(help);
        options.addOption(indiropt);
        options.addOption(outdiropt);
        options.addOption(portopt);
        options.addOption(romanopt);

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        String indir = cmd.getOptionValue("indir");

        boolean roman = false;
        if(cmd.hasOption("roman")){
            roman = true;
        }

        labelcolors = new HashMap<>();
        // put some common label colors here.
        labelcolors.put("PER", "yellow");
        labelcolors.put("LOC", "greenyellow");
        labelcolors.put("GPE", "coral");
        labelcolors.put("MISC", "coral");
        labelcolors.put("ORG", "lightblue");

        int port = Integer.parseInt(cmd.getOptionValue("port", "8080"));

        System.out.println("Reading from "+ indir);

        id2html = new HashMap<>();

        File[] files = (new File(indir)).listFiles();

        HashSet<String> labels = new HashSet<>();

        // load TextAnnotations and create a list.
        List<TextAnnotation> tas = new ArrayList<>();
        for(File f : files) {
            try {
                TextAnnotation ta = SerializationHelper.deserializeTextAnnotationFromFile(f.getAbsolutePath(), true);

                // add a dummy view if
                if(!ta.hasView(ViewNames.NER_CONLL)){
                    View ner = new View(ViewNames.NER_CONLL, "DocumentController",ta,1.0);
                    ta.addView(ViewNames.NER_CONLL, ner);
                }else{
                    for(Constituent c : ta.getView(ViewNames.NER_CONLL).getConstituents()){
                        labels.add(c.getLabel());
                    }
                }

                tas.add(ta);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        ArrayList<String> stylecss = LineIO.readFromClasspath("BOOT-INF/classes/static/css/style.css");

        StringBuilder sb_css = new StringBuilder();
        for(String l : stylecss){
            sb_css.append(l);
        }

        for(String label : labels){
            String color;
            if(labelcolors.containsKey(label)){
                color = labelcolors.get(label);
            }else{
                Random random = new Random();
                int nextInt = random.nextInt(256*256*256);
                color = String.format("#%06x", nextInt);
            }
            sb_css.append("." + label + "{background-color: " + color + "}");
        }

        String bootstrap = "<link rel='stylesheet' href='https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/css/bootstrap.min.css' crossorigin='anonymous'>";
        String index = "<html><head>"+bootstrap+"</head><body><div class='container'>";

        // Some style features are not wanted in this version. In particular: we want to
        // be able to select text.
        String overrides = ".pointer{cursor:auto}" +
                ".text{-webkit-user-select: text;-moz-user-select: text;-ms-user-select: text;}" +
                ".token {padding:0px;border:0px}";

        // every document will be a link on the index.
        StringBuilder listoflinks = new StringBuilder();

        String labellegend = "";
        for(String label : labels){
            labellegend += "<span class='"+label+"'>"+label+"</span>";
        }

        // we do a second loop so we can discover the labels.
        for(TextAnnotation ta : tas){
            String html = HtmlGenerator.getHTMLfromTA(ta, null, false, roman);

            html = "<html><head>"+bootstrap+"<style>"+ sb_css.toString() +overrides +"</style></head><body><div class='container'><div><a href='/'>Back to list</a></div><div>"+labellegend+"</div>"+ html +"</div></body></html>";

            listoflinks.append("<a href='"+ta.getId()+"'>" + ta.getId() + "</a><br />");

            html = html.replaceAll("</span><span", "</span> <span");

            id2html.put(ta.getId(), html);
        }


        index = index + listoflinks.toString() + "</div></body></html>";
        id2html.put("index", index);


        System.out.println("Server started on " + port);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new MyHandler());
        server.setExecutor(null); // creates a default executor
        server.start();

    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {

            URI uri = t.getRequestURI();
            String arg = uri.getPath().substring(1);

            String response;
            if(arg.length() == 0 || arg.equals("index") || arg.equals("index.html")){
                response = id2html.get("index");
            }else {
                response = id2html.getOrDefault(arg, "<html><head></head><body><div>Oops no ta with id: " + arg + "</div></body></html>");
            }

            String encoding = "UTF-8";
            t.getResponseHeaders().set("Content-Type", "text/html; charset=" + encoding);

            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            t.sendResponseHeaders(200, bytes.length);
            OutputStream os = t.getResponseBody();
            os.write(bytes);
            os.close();

        }
    }

}
