package port;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import port.compat.javax.xml.bind.annotation.*;
import port.runtime.PortXmlBeans;

/**
 * Ronda 80 (frente S): @XmlID/@XmlIDREF en PortXmlBeans. Las clases son espejos sintéticos de la forma
 * (nombres XML y anotaciones, según javap) de AnimNode/Anim2DBlend/Anim2DBlendTriangle; no son código del juego.
 * AnimLayer.applyBlendField compara por identidad los nodos de los triángulos con los de m_2DBlends.
 */
class PortXmlBeansIdrefTest {
    @XmlType(name = "Anim2DBlend")
    public static class Blend {
        @XmlElement(name = "m_AnimName") public String animName = "";
        @XmlElement(name = "m_XPos") public float posX = 0f;
        @XmlElement(name = "m_YPos") public float posY = 0f;
        @XmlElement(name = "m_SpeedScale") public float speedScale = 1f;
        @XmlAttribute(name = "referenceID") @XmlID public String referenceId;
    }

    @XmlType(name = "Anim2DBlendTriangle")
    public static class Tri {
        @XmlIDREF @XmlElement(name = "node1") public Blend node1;
        @XmlIDREF @XmlElement(name = "node2") public Blend node2;
        @XmlIDREF @XmlElement(name = "node3") public Blend node3;
    }

    @XmlRootElement(name = "animNode")
    public static class Node {
        @XmlElement(name = "m_Name") public String name = "";
        @XmlElement(name = "m_2DBlends") public List<Blend> blends2d = new ArrayList<>();
        @XmlElement(name = "m_2DBlendTri") public List<Tri> blendTris = new ArrayList<>();
    }

    @Test void idrefsResolveToTheDeclaredInstanceBackwardAndForward() throws Exception {
        String xml = "<animNode><m_Name>n</m_Name>"
                + "<m_2DBlendTri><node1>b</node1><node2> a </node2><node3>missing</node3></m_2DBlendTri>"
                + "<m_2DBlends referenceID=\"a\"><m_AnimName>A</m_AnimName><m_XPos>0.5</m_XPos></m_2DBlends>"
                + "<m_2DBlendTri><node1>a</node1><node2>b</node2></m_2DBlendTri>"
                + "<m_2DBlends referenceID=\"b\"><m_AnimName>B</m_AnimName></m_2DBlends></animNode>";
        Node node = (Node) PortXmlBeans.unmarshal(Node.class, root(xml));
        Blend a = node.blends2d.get(0), b = node.blends2d.get(1);
        assertEquals(2, node.blends2d.size());
        assertEquals(0.5f, a.posX);
        assertEquals(1f, b.speedScale, "field initializer survives an absent element");
        Tri first = node.blendTris.get(0), second = node.blendTris.get(1);
        assertSame(b, first.node1, "forward reference");
        assertSame(a, first.node2, "whitespace-collapsed reference");
        assertNull(first.node3, "unresolved IDREF stays null, as in the RI");
        assertSame(a, second.node1, "backward reference");
        assertSame(b, second.node2);
        assertNull(second.node3);
    }

    @Test void realPlayerAnimSetsPointEveryTriangleAtTheirOwnBlends() throws Exception {
        String home = System.getenv("PZ_HOME");
        Assumptions.assumeTrue(home != null, "PZ_HOME no definido");
        Path player = Path.of(home, "media", "AnimSets", "player");
        for (String file : List.of("movement/defaultWalk.xml", "idle/Idle.xml")) {
            Path path = player.resolve(file);
            Assumptions.assumeTrue(Files.isRegularFile(path), path + " ausente");
            Node node = (Node) PortXmlBeans.unmarshal(Node.class, root(Files.readString(path, StandardCharsets.UTF_8)));
            assertFalse(node.blends2d.isEmpty(), file);
            assertFalse(node.blendTris.isEmpty(), file);
            for (Tri tri : node.blendTris)
                for (Blend vertex : new Blend[] {tri.node1, tri.node2, tri.node3}) {
                    assertNotNull(vertex, file);
                    assertTrue(node.blends2d.stream().anyMatch(blend -> blend == vertex), file + ": " + vertex.referenceId);
                    assertFalse(vertex.animName.isEmpty(), file);
                }
        }
    }

    @Test void marshalWritesTheReferencedId() throws Exception {
        Node node = new Node();
        Blend blend = new Blend();
        blend.referenceId = "7";
        node.blends2d.add(blend);
        Tri tri = new Tri();
        tri.node1 = blend;
        node.blendTris.add(tri);
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        var portDocument = (port.compat.org.w3c.dom.Document) wrap(document);
        PortXmlBeans.marshal(node, portDocument, portDocument);
        var written = document.getDocumentElement().getElementsByTagName("node1");
        assertEquals(1, written.getLength());
        assertEquals("7", written.item(0).getTextContent());
    }

    private static port.compat.org.w3c.dom.Element root(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return (port.compat.org.w3c.dom.Element) wrap(document.getDocumentElement());
    }

    /** Adapts the JDK DOM to the port DOM interfaces by method name (the port interfaces mirror org.w3c.dom). */
    private static Object wrap(Object node) {
        if (node == null) return null;
        Class<?> face = node instanceof org.w3c.dom.Document ? port.compat.org.w3c.dom.Document.class
                : node instanceof org.w3c.dom.Element ? port.compat.org.w3c.dom.Element.class
                : node instanceof org.w3c.dom.Text ? port.compat.org.w3c.dom.Text.class
                : port.compat.org.w3c.dom.Node.class;
        return Proxy.newProxyInstance(face.getClassLoader(), new Class<?>[] {face, Unwrap.class}, (proxy, method, args) -> {
            if (method.getName().equals("unwrap")) return node;
            if (method.getDeclaringClass() == Object.class) return method.invoke(node, args);
            Object[] real = args == null ? null : Arrays.stream(args)
                    .map(arg -> arg instanceof Unwrap unwrap ? unwrap.unwrap() : arg).toArray();
            Class<?>[] types = Arrays.stream(method.getParameterTypes())
                    .map(type -> type.getName().startsWith("port.compat.org.w3c.dom.") ? org.w3c.dom.Node.class : type)
                    .toArray(Class<?>[]::new);
            Method target = Class.forName("org.w3c.dom." + method.getDeclaringClass().getSimpleName())
                    .getMethod(method.getName(), types);
            Object result;
            try { result = target.invoke(node, real); }
            catch (InvocationTargetException error) { throw error.getCause(); }
            return result instanceof org.w3c.dom.Node child ? wrap(child) : result;
        });
    }

    interface Unwrap { Object unwrap(); }
}
