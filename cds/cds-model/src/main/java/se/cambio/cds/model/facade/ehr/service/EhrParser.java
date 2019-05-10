package se.cambio.cds.model.facade.ehr.service;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.openehr.rm.datatypes.basic.DataValue;
import org.openehr.rm.datatypes.text.DvCodedText;
import org.openehr.rm.datatypes.text.DvText;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import se.cambio.cds.model.instance.ArchetypeReference;
import se.cambio.cds.model.instance.ElementInstance;
import se.cambio.cds.util.Domains;

public class EhrParser {
   
    //Accede a la instancia XML y lee el dato al que apunta el path GDL
    public Node queryXML(Document ehrDom, String GDLPath) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
    	XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xpath = xPathFactory.newXPath();    
        
    	//Para añadir el nodo root de la instancia al inicio del path. Ej: /OBSERVATION[at0000]
    	String XPath = "/*";        
        XPathExpression xPathExpr = xpath.compile(XPath);
        Node root = (Node) xPathExpr.evaluate(ehrDom, XPathConstants.NODE);        
    	
        //Pasamos a XPath el path de GDL y ejecutamos contra el XML instancia para recuperar el nodo
        XPath = GDLPathToXPath(GDLPath, root);
        xPathExpr = xpath.compile(XPath);
        Node node = (Node) xPathExpr.evaluate(ehrDom, XPathConstants.NODE);
        
        return node;
    }
   
    //Obtiene el nombre del nodo en el XML instancia
    public static String readClassName(Node node) {
        return node.getNodeName();
}

    //Obtiene el valor del atributo que le pasamos del nodo que le pasamos
    public static String readAttributeValue(Node node, String attr) {
        NamedNodeMap map = node.getAttributes();
        Node value = map.getNamedItem(attr);
        return value.getTextContent();
}


    //Pasa un path de GDL a XPath
    public static String GDLPathToXPath(String gdlPath, Node root) {
    	String className = readClassName(root);
    	String attrValue = readAttributeValue(root, "archetype_node_id");        
    	String xPath = gdlPath.substring(gdlPath.indexOf('/'));
    	xPath = className+"["+attrValue+"]"+ xPath;
    	xPath = xPath.replaceAll("\\[", "[@archetype_node_id='");
    	xPath = xPath.replaceAll("]", "']");
//    	xPath += "/text()";        
        return xPath;
    }   
   
    ///Genera un ArchetypeReference con el dato leído de la instancia XML
    public  ArchetypeReference generateARFromNode(NodeList node, String XMLName, String GDLPath) {
        ArchetypeReference ar = new ArchetypeReference(Domains.EHR_ID, XMLName, "??");
       
        DataValue dataValue = new DvCodedText("prueba", "PRUEBA", node.item(0).getNodeValue());
        new ElementInstance(XMLName+GDLPath, dataValue, ar, null, null);       
        dataValue = new DvText("Prueba DV_TEXT");
        new ElementInstance(XMLName+GDLPath+"1", dataValue, ar, null, null);

        return ar;
    }
   
    //Imprime por pantalla el dato leído de la instancia XML
    public void printResults(NodeList node) {
         for (int i = 0; i < node.getLength(); i++) {
             System.out.println(node.item(i).getNodeValue());
         }        
    }
}