package io.github.mayhewsw.utils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.core.utilities.SerializationHelper;
import org.apache.commons.cli.*;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TalenCLI {

    private static HashMap<String, String> id2filepath;

    private static HashSet<String> labels = new HashSet<>();
    private static HashMap<String, String> labelcolors;
    private static boolean roman = false;

    private static List<String> listoflinks;


    private static String bootstrap = "<link rel='stylesheet' href='https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/css/bootstrap.min.css' crossorigin='anonymous'>";


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

        if(cmd.hasOption("roman")){
            roman = true;
        }

        String indir = cmd.getOptionValue("indir");

        labelcolors = new HashMap<>();
        // put some common label colors here.
        labelcolors.put("PER", "yellow");
        labelcolors.put("PERSON", "yellow");
        labelcolors.put("LOC", "greenyellow");
        labelcolors.put("GPE", "coral");
        labelcolors.put("MISC", "coral");
        labelcolors.put("ORG", "lightblue");

        int port = Integer.parseInt(cmd.getOptionValue("port", "8080"));

        System.out.println("Looking at "+ indir);

        id2filepath = new HashMap<>();

        File[] files = (new File(indir)).listFiles();

        // load TextAnnotations and create a list.
        // don't do this til later.
        // load on demand.
        // this means that you can't know about all labels (potentially?)
        // every document will be a link on the index.

        listoflinks = new ArrayList<>();

        StringBuilder linkstrings = new StringBuilder();
        for(File f : files) {
            id2filepath.put(f.getName(), f.getAbsolutePath());
            linkstrings.append("<a href='"+f.getName() + "'>" + f.getName() + "</a><br />");
            listoflinks.add(f.getName());
        }

        String index = "<html><head>"+bootstrap+"</head><body><div class='container'>";
        index += "<h1>" + indir + "</h1>";
        index = index + linkstrings.toString() + "</div></body></html>";
        id2filepath.put("index", index);


        System.out.println("Server started on " + port);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new MyHandler());
        server.setExecutor(null); // creates a default executor
        server.start();

    }

    @NotNull
    private static String loadTextAnnotation(String docid) throws Exception {
        TextAnnotation ta = SerializationHelper.deserializeTextAnnotationFromFile(docid, true);

        // add a dummy view if no NER view
        if(ta.hasView(ViewNames.NER_CONLL)){
            for(Constituent c : ta.getView(ViewNames.NER_CONLL).getConstituents()){
                labels.add(c.getLabel());
            }
        }else if(ta.hasView(ViewNames.NER_ONTONOTES)) {
            for (Constituent c : ta.getView(ViewNames.NER_ONTONOTES).getConstituents()) {
                labels.add(c.getLabel());
            }
        }else{
            View ner = new View(ViewNames.NER_CONLL, "DocumentController",ta,1.0);
            ta.addView(ViewNames.NER_CONLL, ner);
        }

        // Some style features are not wanted in this version. In particular: we want to
        // be able to select text.
        String overrides = ".pointer{cursor:auto}" +
                ".text{-webkit-user-select: text;-moz-user-select: text;-ms-user-select: text;}" +
                ".token {padding:0px;border:0px}";


        String labellegend = "";
        for(String label : labels){
            labellegend += "<span class='"+label+"'>"+label+"</span>";
        }

        String html = HtmlGenerator.getHTMLfromTA(ta, null, false, roman);

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
                Random r = new Random();
                Color c = Color.getHSBColor(r.nextFloat(), 0.4f, 0.8f);
                color = String.format("#%02x%02x%02x", c.getRed(), c.getBlue(), c.getGreen());
                //int nextInt = label.hashCode() % 256*256*256;
                //color = String.format("#%06x", hex);
                
                labelcolors.put(label, color);
            }
            sb_css.append("." + label + "{background-color: " + color + "}");
        }

        // sometimes the id is a path.
        File f = new File(ta.getId());
        int ind = listoflinks.indexOf(f.getName());
        String prevstring = "";
        String nextstring = "";

        if(ind > 0) {
            String prevdocid = listoflinks.get(ind - 1);
            prevstring = "<a href='/"+prevdocid+"' class=\"btn btn-outline-primary btn-sm\" role=\"button\">< Prev</a>";

        }

        if(ind < listoflinks.size() - 1) {
            String nextdocid = listoflinks.get(ind + 1);
            nextstring = "<a href='/"+nextdocid+"' class=\"btn btn-outline-primary btn-sm\" role=\"button\">Next ></a>";
        }

        // HALP. Does anyone know any good templating libraries??
        html = "<html><head>"+bootstrap+"<style>"+ sb_css.toString() +overrides +"</style></head><body><div class='container'>" +
                "<p><div class=\"btn-toolbar\" role=\"toolbar\" aria-label=\"Toolbar with button groups\">" +
                "<div class='btn-group btn-group-sm mr-2' role='group'>"+
                "<a href='/' class=\"btn btn-outline-secondary btn-sm\" role=\"button\">Back to list</a>" +
                "</div>" +
                "<div class='btn-group btn-group-sm mr-2' role='group'>"+
                prevstring +
                nextstring +
                "</div>" +
                "</div></p>" +
                "<p>"+labellegend+"</p>"+ html +"" +
                "</div>" +
                "</body>" +
                "</html>";

        html = html.replaceAll("</span><span", "</span> <span");

        return html;
    }


    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {

            URI uri = t.getRequestURI();
            String arg = uri.getPath().substring(1);

            String html;
            if(arg.length() == 0 || arg.equals("index") || arg.equals("index.html")){
                // this is a special case.
                html = id2filepath.get("index");
            }else {
                // start with a default message
                html = "<html><head></head><body><div>Oops no ta with id: " + arg + "</div></body></html>";
                if(id2filepath.containsKey(arg)){
                    String filepath = id2filepath.get(arg);
                    try {
                        html = loadTextAnnotation(filepath);
                    } catch (Exception e) {
                        e.printStackTrace();
                        html = "<html><head></head><body><div>Whoops something wrong with doc id: " + arg + "</div></body></html>";
                    }
                }
            }

            String encoding = "UTF-8";
            t.getResponseHeaders().set("Content-Type", "text/html; charset=" + encoding);

            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            t.sendResponseHeaders(200, bytes.length);
            OutputStream os = t.getResponseBody();
            os.write(bytes);
            os.close();

        }
    }

}
