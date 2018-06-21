package io.github.mayhewsw.utils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class TalenCLI {

    private static HashMap<String, String> id2html;

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
                .desc("Which port to use. Default is 8008")
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

        int port = Integer.parseInt(cmd.getOptionValue("port", "8008"));

        System.out.println("Reading from "+ indir);

        id2html = new HashMap<>();

        File[] files = (new File(indir)).listFiles();

        //ClassLoader classLoader = TalenCLI.class.getClassLoader();
        //InputStream stylefile = classLoader.getResourceAsStream();

        ArrayList<String> stylecss = LineIO.readFromClasspath("BOOT-INF/classes/static/css/style.css");
        ArrayList<String> labelcss = LineIO.readFromClasspath("BOOT-INF/classes/static/css/labels.css");

        StringBuilder sb_css = new StringBuilder();
        for(String l : stylecss){
            sb_css.append(l);
        }

        for(String l : labelcss){
            sb_css.append(l);
        }

        String bootstrap = "<link rel='stylesheet' href='https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/css/bootstrap.min.css' crossorigin='anonymous'>";
        String index = "<html><head>"+bootstrap+"</head><body><div class='container'>";
        StringBuilder listoflinks = new StringBuilder();

        String overrides = ".pointer{cursor:auto}" +
                ".text{-webkit-user-select: text;-moz-user-select: text;-ms-user-select: text;}" +
                ".token {padding:0px;border:0px}";

        for(File f : files) {
            try {

                TextAnnotation ta = SerializationHelper.deserializeTextAnnotationFromFile(f.getAbsolutePath(), true);

                String html = HtmlGenerator.getHTMLfromTA(ta, null, false, roman);

                html = "<html><head>"+bootstrap+"<style>"+ sb_css.toString() +overrides +"</style></head><body><div class='container'><a href='/'>Back to list</a><br />"+ html +"</div></body></html>";

                listoflinks.append("<a href='"+ta.getId()+"'>" + ta.getId() + "</a><br />");

                html = html.replaceAll("</span><span", "</span> <span");

                id2html.put(ta.getId(), html);

            } catch (Exception e) {
                e.printStackTrace();
            }
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
