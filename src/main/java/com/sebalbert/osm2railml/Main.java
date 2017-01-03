/**
 osm2railML - creating railML infrastructure from OpenStreetMap data
 Copyright (C) 2016  Sebastian Albert

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.

 */
package com.sebalbert.osm2railml;

import com.sebalbert.osm2railml.osm.Node;
import com.sebalbert.osm2railml.osm.OsmExtract;
import com.sebalbert.osm2railml.osm.Way;
import org.railml.schemas._2016.*;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Main executable.
 *
 */
public class Main
{

    /**
     * Reads XML from OpenStreetMap in order to generate railML infrastructure from it
     * @param args - first argument is expected to be a local (relative) filename
     * @throws JAXBException
     */
    public static void main( String[] args ) throws JAXBException, MalformedURLException, SAXException {
        OsmExtract osm = OsmExtract.fromFile(new File(args[0]));
        for (Node n : osm.nodes)
            System.out.println(n.id + ": " + n.lat + "/" + n.lon + " [" + n.wayRefs.size() + " - " + n.wayRefs.get(0).way.id);
        for (Way w : osm.ways)
            System.out.println(w.id + ":" + w.nd.size() + " - " + w.nd.get(0).node.id + " [" + w.tags.size() + " - railway:" + w.getTag("railway"));

        Infrastructure is = new Infrastructure();
        is.setId("is");
        ETracks tracks = new ETracks();
        is.setTracks(tracks);
        tracks.getTrack().addAll(osm.ways.parallelStream().map(wayToTrack).collect(Collectors.toList()));
        referencesToBeSet.entrySet().parallelStream().forEach(e -> e.getValue()
                .forEach(c -> c.accept(objectById.get(e.getKey()))));
        JAXBContext jc = JAXBContext.newInstance(Infrastructure.class);
        Marshaller marshaller = jc.createMarshaller();
        // SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        // Schema schema = schemaFactory.newSchema(new URL("http://www.railml.org/files/download/schemas/2016/railML-2.3/schema/infrastructure.xsd"));
        // marshaller.setSchema(schema);
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(is, System.out);
    }

    private static Function<Way, ETrack> wayToTrack = way -> {
        ETrack t = new ETrack();
        t.setId("w_" + way.id);
        ETrackTopology topo = new ETrackTopology();
        t.setTrackTopology(topo);
        ETrackBegin tB = new ETrackBegin();
        topo.setTrackBegin(tB);
        tB.setId("tB_" + way.id);
        setTrackBeginOrEnd(tB, way.nd.getFirst());
        ETrackEnd tE = new ETrackEnd();
        topo.setTrackEnd(tE);
        tE.setId("tE_" + way.id);
        setTrackBeginOrEnd(tE, way.nd.getLast());
        return t;
    };

    private static void setTrackBeginOrEnd(ETrackNode trackNode, Way.NodeRef nd) {
        switch (nd.node.wayRefs.size()) {
            case 1:
                TOpenEnd openEnd = new TOpenEnd();
                openEnd.setId("openEnd_" + nd.node.wayRefs.get(0).node.id);
                trackNode.setOpenEnd(openEnd);
                break;
            case 2:
                Way.NodeRef otherWayRef = nd.node.wayRefs.stream().filter(r -> r.way != nd.way).findAny()
                        .orElseThrow(() -> new RuntimeException("Way " + nd.way.id + " contains a node twice"));
                TConnectionData conn = new TConnectionData();
                String thisConnId = "conn_" + nd.way.id + "_" + nd.node.id;
                String thatConnId = "conn_" + otherWayRef.way.id + "_" + nd.node.id;
                conn.setId(thisConnId);
                objectById.put(thisConnId, conn);
                setReferenceLater(thatConnId, ref -> conn.setRef(ref));
                trackNode.setConnection(conn);
        }
    }

    private static Map<String, Object> objectById = Collections.synchronizedMap(new HashMap<>());
    private static Map<String, List<Consumer<Object>>> referencesToBeSet = Collections.synchronizedMap(new HashMap<>());
    private static void setReferenceLater(String id, Consumer<Object> c) {
        Object o = objectById.get(id);
        if (o != null) {
            c.accept(o);
            return;
        }
        List<Consumer<Object>> list = referencesToBeSet.get(id);
        if (list == null) {
            list = Collections.synchronizedList(new LinkedList<>());
            referencesToBeSet.put(id, list);
        }
        list.add(c);
    }

}
