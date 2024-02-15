/*
 * Copyright 2007 Guy Van den Broeck
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.outerj.daisy.diff;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import org.xml.sax.ContentHandler;

public class XslFilter {

    public ContentHandler xsl(ContentHandler consumer, String xslPath)
            throws IOException {

        try {
            // Create transformer factory
            TransformerFactory factory = TransformerFactory.newInstance();

            // Use the factory to create a template containing the xsl file
            Templates template = factory.newTemplates(new StreamSource(
                    getClass().getClassLoader().getResourceAsStream(xslPath)));

            // Use the template to create a transformer
            TransformerFactory transFact = TransformerFactory.newInstance();
            SAXTransformerFactory saxTransFact = (SAXTransformerFactory) transFact;
            // create a ContentHandler
            TransformerHandler transHand = saxTransFact
                    .newTransformerHandler(template);

            transHand.setResult(new SAXResult(consumer));

            return transHand;

        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (TransformerFactoryConfigurationError e) {
            e.printStackTrace();
        }
        throw new IllegalStateException("Can't transform xml.");

    }

    public ContentHandler xsl(ContentHandler consumer, String xslPath, String variableContent) throws IOException {
        try {
            // Create transformer factory
            TransformerFactory factory = TransformerFactory.newInstance();

            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(xslPath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder xslContentBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("{{variable_content_value}}")) {
                    line = line.replace("{{variable_content_value}}", variableContent);
                }
                xslContentBuilder.append(line).append("\n");
            }
            reader.close();

            // Use the factory to create a template containing the xsl file
            Templates template = factory.newTemplates(new StreamSource(
                new StringReader(xslContentBuilder.toString())));

            // Use the template to create a transformer
            TransformerFactory transFact = TransformerFactory.newInstance();
            SAXTransformerFactory saxTransFact = (SAXTransformerFactory) transFact;
            // create a ContentHandler
            TransformerHandler transHand = saxTransFact
                    .newTransformerHandler(template);

            transHand.setResult(new SAXResult(consumer));
            return transHand;
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (TransformerFactoryConfigurationError e) {
            e.printStackTrace();
        }
        throw new IllegalStateException("Can't transform xml.");
    }
}
