package org.outerj.daisy.diff;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.outerj.daisy.diff.html.HTMLDiffer;
import org.outerj.daisy.diff.html.HtmlSaxDiffOutput;
import org.outerj.daisy.diff.html.TextNodeComparator;
import org.outerj.daisy.diff.html.dom.DomTreeBuilder;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class Main {
  static boolean quietMode = false;

    public static void main(String[] args) throws URISyntaxException {

        if (args.length < 2)
            help();

        boolean htmlDiff = true;
        boolean htmlOut = true;
        String outputFileName = "daisydiff.htm";
        String[] css = new String[]{};
        String[] js = new String[]{};
        boolean doxygenHTML = false;

        InputStream oldStream = null;
        InputStream newStream = null;

        try {
            for (int i = 2; i < args.length; i++) {
                String[] split = args[i].split("=");
                if (split[0].equalsIgnoreCase("--file")) {
                    outputFileName = split[1];
                } else if (split[0].equalsIgnoreCase("--type")) {
                    if (split[1].equalsIgnoreCase("tag")) {
                        htmlDiff = false;
                    }
                } else if (split[0].equalsIgnoreCase("--css")) {
                    css = split[1].split(";");
                } else if (split[0].equalsIgnoreCase("--js")) {
                    js = split[1].split(";");
                } else if (split[0].equalsIgnoreCase("--output")) {
                    if (split[1].equalsIgnoreCase("xml")) {
                        htmlOut = false;
                    }
                } else if (split[0].equals("--doxygen")) {
                    doxygenHTML = true;
                } else if (split[0].equals("--q")){
                  quietMode = true;
                } else{
                    help();
                }

            }
            File outputFile= new File(outputFileName);
            try {
                outputFile.createNewFile(); // Fail if outputFileName is malformed. Otherwise result.setResult() below would silently supress an exception (at least with jdk1.8.0_65). Then calling postProcess.endDocument() below would fail with confusing "javax.xml.transform.TransformerException: org.xml.sax.SAXException: setResult() must be called prior to startDocument()."
            }
            catch( IOException e ) {
                System.err.println( "Filepath " +outputFileName+ " is malformed, or some of its folders don't exist, or you don't have write access." );
                return;
            }
            if (!quietMode){
              System.out.println("Daisy Diff https://github.com/DaisyDiff/DaisyDiff");
              System.out.println("Comparing documents: " +args[0]+ " and " +args[1] );
              System.out.println( "Diff type: " +(
                htmlDiff
                ? "html"
                : "tag"
              ) );
              System.out.println("Writing "+(htmlOut?"html":"xml")+" output to " + outputFileName);
            }

            if(css.length>0){
                if (!quietMode)
                  System.out.println("Adding external css files:");
                for(String cssLink:css){
                    System.out.println("  "+cssLink);
                }
            }
            if (js.length > 0) {
                if (!quietMode) {
                    System.out.println("Adding external js files:");
                    for (String jsLink : js) {
                        System.out.println("  " + jsLink);
                    }
                }
            }
            if (!quietMode){
              System.out.println("");
              System.out.print(".");
            }
            SAXTransformerFactory tf = (SAXTransformerFactory) TransformerFactory
                    .newInstance();

            TransformerHandler result = tf.newTransformerHandler();
            // If the file path were malformed, then the following
            result.setResult(new StreamResult(outputFile));


            if (args[0].startsWith("http://")) {
                oldStream = new URI(args[0]).toURL().openStream();
            }
            else {
                oldStream = new FileInputStream(args[0]);
            }
            if (args[1].startsWith("http://")) {
                newStream = new URI(args[1]).toURL().openStream();
            }
            else {
                newStream = new FileInputStream(args[1]);
            }

            XslFilter filter = new XslFilter();

            if (htmlDiff) {
                ContentHandler postProcess = result;
                if (doxygenHTML) {
                    String doxygenJSContent = getDoxygenJS(args[1]);
                    postProcess = htmlOut ? filter.xsl(result,
                            "xslfilter/htmlheader.xsl",
                            doxygenJSContent) : result;
                } else {
                    postProcess = htmlOut ? filter.xsl(result,
                            "xslfilter/htmlheader.xsl") : result;
                }

                Locale locale = Locale.getDefault();
                String prefix = "diff";

                HtmlCleaner cleaner = new HtmlCleaner();

                InputSource oldSource = new InputSource(oldStream);
                InputSource newSource = new InputSource(newStream);

                DomTreeBuilder oldHandler = new DomTreeBuilder();
                cleaner.cleanAndParse(oldSource, oldHandler);
                System.out.print(".");
                TextNodeComparator leftComparator = new TextNodeComparator(
                        oldHandler, locale);

                DomTreeBuilder newHandler = new DomTreeBuilder();
                cleaner.cleanAndParse(newSource, newHandler);
                System.out.print(".");
                TextNodeComparator rightComparator = new TextNodeComparator(
                        newHandler, locale);

                postProcess.startDocument();
                postProcess.startElement("", "diffreport", "diffreport",
                        new AttributesImpl());
                doCSS(css, postProcess);
                doJS(js, postProcess);
                postProcess.startElement("", "diff", "diff",
                        new AttributesImpl());
                HtmlSaxDiffOutput output = new HtmlSaxDiffOutput(postProcess,
                        prefix);

                HTMLDiffer differ = new HTMLDiffer(output);
                differ.diff(leftComparator, rightComparator);
                System.out.print(".");
                postProcess.endElement("", "diff", "diff");
                postProcess.endElement("", "diffreport", "diffreport");
                postProcess.endDocument();

            } else {

                ContentHandler postProcess = htmlOut? filter.xsl(result,
                        "xslfilter/tagheader.xsl"):result;
                postProcess.startDocument();
                postProcess.startElement("", "diffreport", "diffreport",
                        new AttributesImpl());
                postProcess.startElement("", "diff", "diff",
                        new AttributesImpl());
                System.out.print(".");


                InputStreamReader oldReader = null;
                BufferedReader oldBuffer = null;

                InputStreamReader newISReader = null;
                BufferedReader newBuffer = null;
                try {
                    oldReader = new InputStreamReader(oldStream);
                    oldBuffer = new BufferedReader(oldReader);

                    newISReader = new InputStreamReader(newStream);
                    newBuffer = new BufferedReader(newISReader);
                    DaisyDiff.diffTag(oldBuffer, newBuffer, postProcess);

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    oldBuffer.close();
                    newBuffer.close();
                    oldReader.close();
                    newISReader.close();
                }


                System.out.print(".");
                postProcess.endElement("", "diff", "diff");
                postProcess.endElement("", "diffreport", "diffreport");
                postProcess.endDocument();
            }

        } catch (Throwable e) {
          if (quietMode){
            System.out.println(e);
          } else {
            e.printStackTrace();
            if (e.getCause() != null) {
                e.getCause().printStackTrace();
            }
            if (e instanceof SAXException) {
                ((SAXException) e).getException().printStackTrace();
            }
            help();
          }
        } finally {
            try {
                if(oldStream != null) oldStream.close();
            } catch (IOException e) {
                //ignore this exception
            }
            try {
                if(newStream != null) newStream.close();
            } catch (IOException e) {
                //ignore this exception
            }
        }
        if (quietMode)
          System.out.println();
        else
          System.out.println("done");

    }

    private static void doCSS(String[] css, ContentHandler handler) throws SAXException {
        handler.startElement("", "css", "css",
                new AttributesImpl());
        for(String cssLink : css){
            AttributesImpl attr = new AttributesImpl();
            attr.addAttribute("", "href", "href", "CDATA", cssLink);
            attr.addAttribute("", "type", "type", "CDATA", "text/css");
            attr.addAttribute("", "rel", "rel", "CDATA", "stylesheet");
            handler.startElement("", "link", "link",
                    attr);
            handler.endElement("", "link", "link");
        }

        handler.endElement("", "css", "css");

    }

    private static void doJS(String[] js, ContentHandler handler) throws SAXException {
        handler.startElement("", "js", "js",
                new AttributesImpl());
        for(String jsLink : js){
            AttributesImpl attr = new AttributesImpl();
            attr.addAttribute("", "type", "type", "CDATA", "text/javascript");
            attr.addAttribute("", "src", "src", "CDATA", jsLink);
            handler.startElement("", "script", "script",
                    attr);
            handler.endElement("", "script", "script");
        }
        handler.endElement("", "js", "js");
    }

    private static String getDoxygenJS(String newFile) {
        String doxygenJs = "";
        if (newFile.startsWith("http://")) {
            return doxygenJs;
        }

        String[] pathComponents = newFile.split("/");
        int size = pathComponents.length;
        int htmlIndex = 0;
        String afterHtmlFilePath = "";
        String parentPath = Paths.get("", new String[]{"..", ".."}).toString();
        while (size > 0) {
            size--;
            if (pathComponents[size] == "html") {
                htmlIndex = size;
                break;
            }
        }
        if (htmlIndex > 0) {
            String[] afterHtmlComponents = Arrays.copyOfRange(pathComponents,
                    htmlIndex + 1, pathComponents.length);
            afterHtmlFilePath = Paths.get("", afterHtmlComponents).toString();
            String[] parentPathComponents = new String[afterHtmlComponents.length - 1];
            Arrays.fill(parentPathComponents, "..");
            parentPath = Paths.get("", parentPathComponents).toString();
        }

        String separator = FileSystems.getDefault().getSeparator();
        String searchPath = Paths.get(parentPath, "search").toString() + separator;
        String menuDataPath = Paths.get(parentPath, "menudata.js").toString();
        String menuPath = Paths.get(parentPath, "menu.js").toString();
        String initMenuPath = parentPath + separator;

        doxygenJs = "<script type=\"text/javascript\">\n" +
                "    var searchBox = new SearchBox(\"searchBox\", \"%SEARCH_PATH%\", '.html');\n" +
                "</script>\n" +
                "<script type=\"text/javascript\" src=\"%MENUDATA_PATH%\"></script>\n" +
                "<script type=\"text/javascript\" src=\"%MENU_PATH%\"></script>\n" +
                "<script type=\"text/javascript\">\n" +
                "    $(function () {\n" +
                "        initMenu('%INIT_MENU_PATH%', true, false, 'search.php', 'Search');\n" +
                "        $(document).ready(function () { init_search(); });\n" +
                "    });\n" +
                "</script>\n" +
                "<script type=\"text/javascript\">\n" +
                "    $(document).ready(function () { initNavTree('%INIT_NAV_TREE_PATH%', '%INIT_RESIZABLE_PATH%'); initResizable(); });\n" +
                "</script>";
        
        doxygenJs = doxygenJs
                .replace("%SEARCH_PATH%", searchPath)
                .replace("%MENUDATA_PATH%", menuDataPath)
                .replace("%MENU_PATH%", menuPath)
                .replace("%INIT_MENU_PATH%", initMenuPath)
                .replace("%INIT_NAV_TREE_PATH%", afterHtmlFilePath)
                .replace("%INIT_RESIZABLE_PATH%", initMenuPath);

        return doxygenJs;
    }

    private static void help() {
        System.out.println("==========================");
        System.out.println("DAISY DIFF HELP:");
        System.out.println("java -jar daisydiff.jar [oldHTML] [newHTML]");
        System.out
                .println("--file=[filename] - Write output to the specified file.");
        System.out
                .println("--type=[html/tag] - Use the html (default) diff algorithm or the tag diff.");
        System.out.println("--css=[cssfile1;cssfile2;cssfile3] - Add external CSS files.");
        System.out.println("--js=[jsfile1;jsfile2;jsfile3] - Add external JS files.");
        System.out.println("--output=[html/xml] - Write html (default) or xml output.");
        System.out.println("--doxygen - Consider html files as Doxygen-generated (applicable if type=html).");
        System.out.println("--q  - Generate less console output.");
        System.out.println("");
        System.out.println("EXAMPLES: ");
        System.out.println("(1)");
        System.out
                .println("java -jar daisydiff.jar http://web.archive.org/web/20070107145418/http://news.bbc.co.uk/ http://web.archive.org/web/20070107182640/http://news.bbc.co.uk/ --css=http://web.archive.org/web/20070107145418/http://news.bbc.co.uk/nol/shared/css/news_r5.css");
        System.out.println("(2)");
        System.out.println("java -jar daisydiff.jar http://cocoondev.org/wiki/291-cd/version/15/part/SimpleDocumentContent/data http://cocoondev.org/wiki/291-cd/version/17/part/SimpleDocumentContent/data --css=http://cocoondev.org/resources/skins/daisysite/css/daisy.css --output=xml --file=daisysite.htm");
        System.out.println("==========================");
        System.exit(0);
    }

}
