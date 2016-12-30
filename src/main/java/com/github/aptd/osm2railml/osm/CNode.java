package com.github.aptd.osm2railml.osm;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * POJO for a Node in the sense of OSM data
 */

@XmlRootElement(name = "node")
public class CNode {

    @XmlAttribute
    public final String id = null, lat = null, lon = null;

    private CNode() { }

}
