package ch.fhnw.dlcopy;

import ch.fhnw.util.ProcessExecutor;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Manipulate XMLboot and GRUB config files.
 */
public class BootConfigUtil {

    private final static Logger LOGGER
            = Logger.getLogger(BootConfigUtil.class.getName());

    private final static ProcessExecutor PROCESS_EXECUTOR
            = new ProcessExecutor();

    /**
     * returns the DataPartitionMode of an installation (out of the xmlboot
     * config file)
     *
     * @param imagePath
     * @return
     */
    public static DataPartitionMode getDataPartitionMode(String imagePath) {
        try {
            File xmlBootConfigFile = getXmlBootConfigFile(new File(imagePath));
            if (xmlBootConfigFile == null) {
                return null;
            }
            org.w3c.dom.Document xmlBootDocument
                    = parseXmlFile(xmlBootConfigFile);
            xmlBootDocument.getDocumentElement().normalize();
            Node persistenceNode = getPersistenceNode(xmlBootDocument);
            NodeList childNodes = persistenceNode.getChildNodes();
            for (int i = 0, length = childNodes.getLength(); i < length; i++) {
                Node childNode = childNodes.item(i);
                String childNodeName = childNode.getNodeName();
                LOGGER.log(Level.FINER,
                        "childNodeName: \"{0}\"", childNodeName);
                if ("option".equals(childNodeName)) {
                    NamedNodeMap optionAttributes = childNode.getAttributes();
                    Node selectedNode
                            = optionAttributes.getNamedItem("selected");
                    if (selectedNode != null) {
                        Node idNode = optionAttributes.getNamedItem("id");
                        String selectedPersistence = idNode.getNodeValue();
                        LOGGER.log(Level.FINER, "selectedPersistence: \"{0}\"",
                                selectedPersistence);
                        switch (selectedPersistence) {
                            case "rw":
                                return DataPartitionMode.READ_WRITE;
                            case "ro":
                                return DataPartitionMode.READ_ONLY;
                            case "no":
                                return DataPartitionMode.NOT_USED;
                        }
                    }
                }
            }
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            LOGGER.log(Level.WARNING, "could not parse xmlboot config", ex);
        }
        LOGGER.warning("could not determine data partition mode");
        return null;
    }

    /**
     * Sets the data partition mode in xmlboot and GRUB
     *
     * @param destinationDataPartitionMode the DataPartitionMode to set
     * @param imagePath the path where the target image is mounted
     */
    public static void setDataPartitionMode(
            DataPartitionMode destinationDataPartitionMode, String imagePath) {

        setDataPartitionModeXmlBoot(destinationDataPartitionMode, imagePath);
        setDataPartitionModeGrub(destinationDataPartitionMode, imagePath);
    }

    private static void setDataPartitionModeXmlBoot(
            DataPartitionMode destinationDataPartitionMode, String imagePath) {

        try {
            File xmlBootConfigFile = getXmlBootConfigFile(new File(imagePath));
            if (xmlBootConfigFile == null) {
                LOGGER.warning("xmlBootConfigFile == null");
                return;
            }
            org.w3c.dom.Document xmlBootDocument
                    = parseXmlFile(xmlBootConfigFile);
            xmlBootDocument.getDocumentElement().normalize();
            Node persistenceNode = getPersistenceNode(xmlBootDocument);
            NodeList childNodes = persistenceNode.getChildNodes();
            for (int i = 0, length = childNodes.getLength(); i < length; i++) {
                Node childNode = childNodes.item(i);
                String childNodeName = childNode.getNodeName();
                LOGGER.log(Level.FINER,
                        "childNodeName: \"{0}\"", childNodeName);
                if ("option".equals(childNodeName)) {
                    NamedNodeMap optionAttributes = childNode.getAttributes();
                    Node idNode = optionAttributes.getNamedItem("id");
                    String id = idNode.getNodeValue();
                    LOGGER.log(Level.FINER, "id: \"{0}\"", id);
                    switch (id) {
                        case "rw":
                            if (destinationDataPartitionMode
                                    == DataPartitionMode.READ_WRITE) {
                                selectNode(childNode);
                            } else {
                                unselectNode(childNode);
                            }
                            break;
                        case "ro":
                            if (destinationDataPartitionMode
                                    == DataPartitionMode.READ_ONLY) {
                                selectNode(childNode);
                            } else {
                                unselectNode(childNode);
                            }
                            break;
                        case "no":
                            if (destinationDataPartitionMode
                                    == DataPartitionMode.NOT_USED) {
                                selectNode(childNode);
                            } else {
                                unselectNode(childNode);
                            }
                            break;
                    }
                }
            }

            TransformerFactory transformerFactory
                    = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(xmlBootDocument);
            StreamResult result = new StreamResult(xmlBootConfigFile);
            transformer.transform(source, result);

            // rebuild bootlogo (if neccessary)
            File parentDir = xmlBootConfigFile.getParentFile();
            LOGGER.log(Level.INFO, "parentDir: {0}", parentDir);
            if (parentDir.getName().equals("bootlogo.dir")) {
                PROCESS_EXECUTOR.executeProcess(true, true, "gfxboot",
                        "--archive", parentDir.getPath(),
                        "--pack-archive", parentDir.getParent() + "/bootlogo");
            }

        } catch (ParserConfigurationException | SAXException | IOException ex) {
            LOGGER.log(Level.WARNING, "could not parse xmlboot config", ex);
        } catch (TransformerException ex) {
            LOGGER.log(Level.WARNING, "could not save xmlboot config", ex);
        }
    }

    private static void setDataPartitionModeGrub(
            DataPartitionMode destinationDataPartitionMode, String imagePath) {
        String persistenceString = "";
        switch (destinationDataPartitionMode) {
            case READ_ONLY:
                persistenceString = "persistence persistence-read-only";
                break;

            case READ_WRITE:
                persistenceString = "persistence";
                break;

            case NOT_USED:
                break;

            default:
                LOGGER.log(Level.WARNING, "unsupported dataPartitionMode: {0}",
                        destinationDataPartitionMode);
        }
        PROCESS_EXECUTOR.executeProcess("sed", "-i", "-e",
                "s|set PERSISTENCE=.*|set PERSISTENCE=\"" + persistenceString
                + "\"|1", imagePath + "/boot/grub/grub.cfg");
    }

    private static Node getPersistenceNode(
            org.w3c.dom.Document xmlBootDocument) {
        Node configsNode
                = xmlBootDocument.getElementsByTagName("configs").item(0);
        NodeList childNodes = configsNode.getChildNodes();
        for (int i = 0, length = childNodes.getLength(); i < length; i++) {
            Node childNode = childNodes.item(i);
            String childNodeName = childNode.getNodeName();
            LOGGER.log(Level.FINER, "childNodeName: \"{0}\"", childNodeName);
            if ("config".equals(childNodeName)) {
                Node idNode = childNode.getAttributes().getNamedItem("id");
                String idNodeValue = idNode.getNodeValue();
                LOGGER.log(Level.FINER, "idNodeValue: \"{0}\"", idNodeValue);
                if ("persistence".equals(idNodeValue)) {
                    return childNode;
                }
            }
        }
        return null;
    }

    private static File getXmlBootConfigFile(File imageDirectory) {
        // search through all known variants
        String[] dirs = new String[]{"isolinux", "syslinux"};
        String[] subdirs = new String[]{"/", "/bootlogo.dir/"};
        for (String dir : dirs) {
            for (String subdir : subdirs) {
                File configFile = new File(
                        imageDirectory, dir + subdir + "xmlboot.config");
                if (configFile.exists()) {
                    LOGGER.log(Level.INFO,
                            "xmlboot config file: {0}", configFile);
                    return configFile;
                } else {
                    LOGGER.log(Level.FINE,
                            "xmlboot config NOT found at {0}", configFile);
                }
            }
        }
        LOGGER.warning("xmlboot config file not found!");
        return null;
    }

    private static org.w3c.dom.Document parseXmlFile(File file)
            throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringComments(true);
        factory.setIgnoringElementContentWhitespace(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(file);
    }

    private static void selectNode(Node node) {
        Element element = (Element) node;
        element.setAttribute("selected", "true");
    }

    private static void unselectNode(Node node) {
        Element element = (Element) node;
        element.removeAttribute("selected");
    }
}
