package com.miravtech.checksbgn;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.miravtech.checksbgn.CheckReport.ERRORCODES;
import com.miravtech.sbgn.ArcType;
import com.miravtech.sbgn.AuxiliaryUnitType;
import com.miravtech.sbgn.CloneMarkerType;
import com.miravtech.sbgn.CompartmentType;
import com.miravtech.sbgn.EntityPoolNodeType;
import com.miravtech.sbgn.EquivalenceArcType;
import com.miravtech.sbgn.LogicArcType;
import com.miravtech.sbgn.LogicalOperatorNodeType;
import com.miravtech.sbgn.ProcessType;
import com.miravtech.sbgn.SBGNGlyphType;
import com.miravtech.sbgn.SBGNNodeType;
import com.miravtech.sbgn.SBGNPDl1;
import com.miravtech.sbgn.SinkType;
import com.miravtech.sbgn.SourceType;
import com.miravtech.sbgn.SubmapType;
import com.miravtech.sbgn.TagType;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.ComplexContent;

/**
 * 
 * Checks the content of an SBGN XML file. The checking includes: - schema check
 *<ul>
 * <li>multiple ID check</li>
 * <li>all refNode IDs exist</li>
 * <li>all cloneref ID exist, and the source is of the same type as the clone</li>
 * <li>cyclic cloneref ID</li>
 * <li>containment (not all elements can be contained in all other elements)</li>
 * <li>type of Nodes involved in Processes (depending on function in the process
 * and process type).</li>
 * </ul>
 * 
 * @author Razvan Popovici
 * 
 */
public class Checker {
	JAXBContext jaxbContext;
	Unmarshaller unmarshaller;

	public Checker() throws JAXBException {
		jaxbContext = JAXBContext
				.newInstance("com.miravtech.sbgn:com.miravtech.sbgn_graphics");
		unmarshaller = jaxbContext.createUnmarshaller();
	}

	public List<CheckReport> check(File f) throws JAXBException, SAXException {
		try {
			return check(f.toURI().toURL());
		} catch (MalformedURLException e) {
			e.printStackTrace(); // should never happen
			return new LinkedList<CheckReport>();
		}
	}

	public List<CheckReport> check(URL f) throws JAXBException, SAXException {

		// check the schema
		List<CheckReport> r = checkSchema(f);
		if (r != null)
			return r;

		r = new LinkedList<CheckReport>();
		// check duplicates
		r.addAll(checkDuplicateID());

		// node references
		r.addAll(checkRefNode());

		// clones check
		r.addAll(checkcloneRefNode());

		// conceptual model
		r.addAll(checkConceptualModel());

		return r; // no errors
	}

	/**
	 * JAXB view of the file.
	 */
	private SBGNPDl1 sbgnpath;

	private List<CheckReport> checkDuplicateID() {

		final Set<String> ids = new HashSet<String>();
		final Set<String> replicate = new HashSet<String>();

		SBGNIterator it = new SBGNIterator() {
			@Override
			public void iterateGlyph(SBGNGlyphType n) {
				String id = n.getID();
				if (id != null) {
					if (ids.contains(id))
						replicate.add(id);
					ids.add(id);
				}
			}
		};

		it.run(sbgnpath);

		final List<CheckReport> ret = new LinkedList<CheckReport>();
		for (String s : replicate) {
			CheckReport err = new CheckReport(ERRORCODES.ERROR_DUPLICATE_ID, s);
			ret.add(err);
		}
		return ret;

	}

	private List<CheckReport> checkRefNode() {

		final Set<String> ids = new HashSet<String>();
		SBGNIterator it = new SBGNIterator() {
			@Override
			public void iterateGlyph(SBGNGlyphType n) {
				String id = n.getID();
				if (id != null) {
					ids.add(id);
				}
			}
		};
		it.run(sbgnpath);

		final List<CheckReport> ret = new LinkedList<CheckReport>();
		SBGNIterator it1 = new SBGNIterator() {
			@Override
			public void iterateArc(ArcType n) {
				if (!ids.contains(n.getRefNode())) {
					ret.add(new CheckReport(ERRORCODES.ERROR_INVALID_REF, n
							.getRefNode()));
				}
			}
		};
		it1.run(sbgnpath);

		return ret;
	}

	private List<CheckReport> checkcloneRefNode() {

		// data structures we need
		final Map<String, SBGNNodeType> ids = new HashMap<String, SBGNNodeType>();
		final Map<SBGNNodeType, String> clone = new HashMap<SBGNNodeType, String>();

		// fill the data structures
		SBGNIterator it = new SBGNIterator() {
			@Override
			public void iterateNode(SBGNNodeType n) {
				String id = n.getID();
				if (id != null) {
					ids.put(id, n);
				}
				String cloneof = n.getCloneref();
				if (cloneof != null)
					clone.put(n, cloneof);
			}
		};
		it.run(sbgnpath);

		// check the invalid cloneref ID
		final List<CheckReport> ret = new LinkedList<CheckReport>();
		for (String s : clone.values()) {
			if (!ids.containsKey(s)) {
				// error, clone not found
				ret.add(new CheckReport(ERRORCODES.ERROR_INVALID_CLONE_REF, s));
			}
		}

		// check type validity
		for (Map.Entry<SBGNNodeType, String> s : clone.entrySet()) {
			SBGNNodeType n1 = s.getKey();
			SBGNNodeType n2 = ids.get(s.getValue());
			if (n1 == null || n2 == null)
				continue; // wrong ID, reported previously

			if (n1.getClass() != n2.getClass()) {
				// error, objects do not have the same type.
				ret.add(new CheckReport(ERRORCODES.ERROR_DIFFERENT_CLONE_TYPES,
						s.getKey().getID(), s.getValue()));
			}

		}

		// check cyclic definitions
		for (SBGNNodeType s : clone.keySet()) {
			Set<SBGNNodeType> stack = new HashSet<SBGNNodeType>();
			SBGNNodeType crt = s;
			while (true) {
				if (stack.contains(crt)) {
					// error, cyclic definition of the symbol s
					ret.add(new CheckReport(ERRORCODES.ERROR_CYCLIC_CLONE_DEF,
							s.getCloneref()));
				}
				stack.add(crt);
				if (crt.getCloneref() == null)
					break; // OK, not cloned, end of the chain
				crt = ids.get(crt.getCloneref());
				if (crt == null)
					break; // id not found, reported previously
			}
		}

		return ret;
	}

	/**
	 * 
	 * Checks the schema and initializes the sbgnpath content
	 * 
	 * @param f
	 *            the file to check
	 * @return
	 * @throws JAXBException
	 * @throws SAXException
	 */
	private List<CheckReport> checkSchema(URL f) throws JAXBException,
			SAXException {

		// enforce schema check
		SchemaFactory sf = SchemaFactory
				.newInstance("http://www.w3.org/2001/XMLSchema");
		sf.setResourceResolver(new MyResourceResolver());
		Schema s = sf.newSchema(SBGNPDl1.class.getResource("/dcthera.xsd"));
		unmarshaller.setSchema(s);

		sbgnpath = null;
		try {
			sbgnpath = (SBGNPDl1) unmarshaller.unmarshal(f);
		} catch (UnmarshalException r) {
			LinkedList<CheckReport> ret = new LinkedList<CheckReport>();

			if (r.getLinkedException() instanceof SAXParseException) {
				SAXParseException e = (SAXParseException) r
						.getLinkedException();
				ret.add(new CheckReport(e));
			} else { // some other error
				ret.add(new CheckReport(r));
			}
			return ret;
		}

		return null; // no errors
	}

	// 3.3Theconceptualmodel
	private List<CheckReport> checkConceptualModel() throws JAXBException,
			SAXException {

		final List<CheckReport> ret = new LinkedList<CheckReport>();

		// check all of the nodes for contaiments
		final Map<String, SBGNGlyphType> ids = new HashMap<String, SBGNGlyphType>();
		SBGNIterator it1 = new SBGNIterator() {
			@Override
			public void iterateNode(SBGNNodeType n) {

				for (SBGNNodeType n1 : n.getInnerNodes())

					ret.addAll(checkNodeContent(n, n1));
			}

			@Override
			public void iterateGlyph(SBGNGlyphType n) {
				if (n.getID() != null)
					ids.put(n.getID(), n);
			}
		};
		it1.run(sbgnpath);

		// build a node-archs map and check the archs
		final Map<SBGNNodeType, Collection<ArcType>> connections = new HashMap<SBGNNodeType, Collection<ArcType>>();
		SBGNIterator it2 = new SBGNIterator() {

			@Override
			public void iterateNode(SBGNNodeType n) {
				if (!connections.containsKey(n)) {
					connections.put(n, new HashSet<ArcType>());
				}
				for (ArcType a : n.getArcs()) {
					SBGNGlyphType g = ids.get(a.getRefNode());
					if (g instanceof SBGNNodeType) {
						SBGNNodeType n1 = (SBGNNodeType) g;
						if (!connections.containsKey(n1)) {
							connections.put(n1, new HashSet<ArcType>());
						}
						connections.get(n).add(a);
						connections.get(n1).add(a);
						CheckReport r = checkArc(a, n, n1);
						if (r != null)
							ret.add(r);
					}
				}
			}
		};
		it2.run(sbgnpath);

		for (Map.Entry<SBGNNodeType, Collection<ArcType>> e : connections
				.entrySet()) {
			CheckReport cr = checkNodeConnections(e.getKey(), e.getValue());
			if (cr != null)
				ret.add(cr);
		}

		return ret;
	}

	private CheckReport checkNodeConnections(SBGNNodeType n,
			Collection<ArcType> arcs) {
		if (n instanceof ProcessType) {
			// test process type and connections
			// TODO
		}

		// not should have exactly one logic arc
		// any other logical node must have at least two logical nodes (AND, OR)
		// a Tag has exactly one equivalence tag.
		// dissociation - one consumption, multiple production
		// association - one production , multiple consumption

		return null;
	}

	/**
	 * Checks the contained nodes of a node
	 * 
	 * @param n
	 * @return
	 */
	private List<CheckReport> checkNodeContent(SBGNNodeType n, SBGNNodeType n1) {
		final List<CheckReport> ret = new LinkedList<CheckReport>();
		if (n instanceof SubmapType) {
			// submaps can only contain labels
			if (!(n1 instanceof TagType)) {
				ret.add(new CheckReport(
						ERRORCODES.SUBMAP_CAN_ONLY_CONTAIN_LABELS, n.getID(),
						n1.getID()));
			}
		}

		if ((n instanceof AuxiliaryUnitType)
				|| (n instanceof LogicalOperatorNodeType)
				|| (n instanceof TagType) || (n instanceof SinkType)
				|| (n instanceof SourceType)
				|| (n instanceof LogicalOperatorNodeType)) {
			// these types cannot contain anything else
			ret.add(new CheckReport(
					ERRORCODES.NODE_CANNOT_CONTAIN_OTHER_GLYPHS, n.getID(), n1
							.getID()));
		}

		// nobody cannot contain another compartment, except a container
		if ((n1 instanceof CompartmentType && !(n instanceof CompartmentType)))
			ret.add(new CheckReport(
					ERRORCODES.COMPARTMENTS_CANNOT_BE_CONTAINED, n.getID(), n1
							.getID()));

		if ((n instanceof ProcessType))
			if (!(n1 instanceof CloneMarkerType))
				ret.add(new CheckReport(
						ERRORCODES.PROCESSES_CANNOT_CONTAIN_OTHER_NODES, n
								.getID(), n1.getID()));

		if ((n instanceof EntityPoolNodeType)) {

			// an entity cannot contain a process, regerence node, logical node
			// operator, source, sync, perturbing agents
			// see "3.4.2 Containment definition"
			// TODO

			if (!(n instanceof ComplexContent)) {
				// a non complex cannot contain any other EPN
				// TODO
			}
		}

		return ret;
	}

	private CheckReport checkArc(ArcType arch, SBGNGlyphType g1,
			SBGNGlyphType g2) {
		if (!(g1 instanceof SBGNNodeType)) {
			// error, each arch must be established between two nodes
			return new CheckReport(ERRORCODES.ERROR_ARCHS_MUST_CONNECT_NODES,
					g1.getID());
		}
		if (!(g2 instanceof SBGNNodeType)) {
			// error, each arch must be established between two nodes
			return new CheckReport(ERRORCODES.ERROR_ARCHS_MUST_CONNECT_NODES,
					g2.getID());
		}
		if (arch instanceof EquivalenceArcType) {
			if (!(g1 instanceof TagType || g2 instanceof TagType)) {
				return new CheckReport(
						ERRORCODES.ERROR_EQUIVALENCE_ARC_MUST_LINK_A_TAG, g1
								.getID(), g2.getID());
			}

		}

		if (arch instanceof LogicArcType) {
			if (!(g1 instanceof LogicalOperatorNodeType || g2 instanceof LogicalOperatorNodeType)) {
				return new CheckReport(
						ERRORCODES.ERROR_LOGIC_ARC_MUST_LINK_A_LOGIC_OPERATOR,
						g1.getID(), g2.getID());
			}
		}

		// any other remaining arc (flux, modulation)
		// must connect a process to an EPN
		if (!(g1 instanceof EntityPoolNodeType || g2 instanceof EntityPoolNodeType)
				|| !(g1 instanceof ProcessType || g2 instanceof ProcessType)) {
			return new CheckReport(
					ERRORCODES.ERROR_ARC_MUST_LINK_AN_EPN_TO_PROCESS, g1
							.getID(), g2.getID());

		}

		// if we are still here, no error was found
		return null;
	}
}

/**
 * 
 * This class provides support for resources finding.
 * 
 * @author Razvan Popovici
 * 
 */
class MyResourceResolver implements LSResourceResolver {
	@Override
	public LSInput resolveResource(String type, String namespaceURI,
			String publicId, String systemId, String baseURI) {
		try {
			URL url = SBGNPDl1.class.getResource("/" + systemId);
			InputSource is = new InputSource(url.openStream());
			LSInput ret = new LSInputSAXWrapper(is);
			ret.setPublicId(publicId);
			ret.setSystemId(systemId);
			ret.setBaseURI(baseURI);
			return ret;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
