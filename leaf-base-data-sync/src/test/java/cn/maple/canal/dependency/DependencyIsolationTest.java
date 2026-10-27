package cn.maple.canal.dependency;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the dependency contract: RocketMQ must only be reachable through the optional
 * {@code leaf-base-rocketmq} module, never through a direct coordinate in this module.
 */
class DependencyIsolationTest {
    @Test
    void shouldDeclareRocketMqOnlyThroughOptionalLeafModule() throws Exception {
        Document document = parsePom();

        NodeList dependencies = document.getElementsByTagName("dependency");
        List<String> rocketMqCoordinates = new ArrayList<>();
        boolean leafModuleFound = false;
        boolean leafModuleOptional = false;

        for (int index = 0; index < dependencies.getLength(); index++) {
            Element dependency = (Element) dependencies.item(index);
            String groupId = textOf(dependency, "groupId");
            String artifactId = textOf(dependency, "artifactId");
            if ("leaf-base-rocketmq".equals(artifactId)) {
                leafModuleFound = true;
                leafModuleOptional = "true".equals(textOf(dependency, "optional"));
            }
            if (groupId != null && groupId.startsWith("org.apache.rocketmq")) {
                rocketMqCoordinates.add(groupId + ":" + artifactId);
            }
        }

        assertTrue(leafModuleFound, "leaf-base-rocketmq must be declared as the single RocketMQ entry point");
        assertTrue(leafModuleOptional, "leaf-base-rocketmq must stay optional so RabbitMQ-only apps are unaffected");
        assertTrue(rocketMqCoordinates.isEmpty(),
                "no direct org.apache.rocketmq coordinate is allowed, found: " + rocketMqCoordinates);
        assertEquals(1, countRocketMqArtifacts(dependencies), "exactly one RocketMQ artifact is allowed");
    }

    private int countRocketMqArtifacts(NodeList dependencies) {
        int count = 0;
        for (int index = 0; index < dependencies.getLength(); index++) {
            String artifactId = textOf((Element) dependencies.item(index), "artifactId");
            if (artifactId != null && artifactId.contains("rocketmq")) {
                count++;
            }
        }
        return count;
    }

    private Document parsePom() throws Exception {
        Path pom = Path.of("pom.xml");
        assertTrue(Files.exists(pom), "module pom.xml must be the working directory of the test run");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(pom.toFile());
    }

    private String textOf(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                return node.getTextContent().trim();
            }
        }
        return null;
    }
}
