/**
 * EPCIS Capture Interface. Converts XML events delivered by HTTP POST into the
 * database format and inserts them. Database stuff is highly schema specific.
 * 
 * @author David Gubler, Alain Remund
 */

package org.accada.epcis.repository;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.accada.epcis.repository.wrapper.BusinessTransaction;
import org.accada.epcis.repository.wrapper.EventFieldExtension;
import org.accada.epcis.repository.wrapper.Vocabulary;
import org.accada.epcis.repository.wrapper.Vocabulary.VociSyntaxException;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * @author Marco Steybe
 */
public class EpcisCaptureInterface extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /**
     * The log to write to.
     */
    private static final Logger LOG = Logger.getLogger(EpcisCaptureInterface.class);

    /**
     * Wheter we should insert new vocabulary or throw an error message.
     */
    private boolean insertMissingVoc = true;

    /**
     * The XML-Validator which validates the incoming messages.
     */
    private static Validator validator = null;

    /**
     * The ObjectEvent-query without data.
     */
    private final String objectEventInsert = "INSERT INTO event_ObjectEvent ("
            + "eventTime, recordTime, eventTimeZoneOffset, bizStep, "
            + "disposition, readPoint, bizLocation, action"
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * The AggregationEvent-query without data.
     */
    private final String aggregationEventInsert = "INSERT INTO event_AggregationEvent ("
            + "eventTime, recordTime, eventTimeZoneOffset, bizStep, "
            + "disposition, readPoint, bizLocation, action, parentID "
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * The QuantityEvent-query without data.
     */
    private final String quantityEventInsert = "INSERT INTO event_QuantityEvent ("
            + "eventTime, recordTime, eventTimeZoneOffset, bizStep, "
            + "disposition, readPoint, bizLocation, epcClass, quantity"
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * The TransactionEvent-query without data.
     */
    private final String transactionEventInsert = "INSERT INTO event_TransactionEvent ("
            + "eventTime, recordTime, eventTimeZoneOffset, bizStep, "
            + "disposition, readPoint, bizLocation, action"
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * The Connection to the database.
     */
    private Connection dbconnection = null;

    /**
     * The DataSource holding the database.
     */
    private DataSource db = null;

    /**
     * SAX needs a static document variable.
     */
    private static Document document = null;

    /**
     * Returns a simple information page.
     */
    public void doGet(final HttpServletRequest req,
            final HttpServletResponse rsp) throws ServletException, IOException {
        rsp.setContentType("text/html");
        final PrintWriter out = rsp.getWriter();

        out.println("<html>");
        out.println("<head><title>EPCIS Capture Service</title></head>");
        out.println("<body>");
        out.println("<p>This service captures ECPIS events sent to it using ");
        out.println("HTTP POST requests. Expected POST parameter name is \"event\", ");
        out.println(" expected payload is an XML binding of an EPCISDocument");
        out.println("containing ObjectEvents, AggregationEvents, QuantityEvents ");
        out.println("and/or TransactionEvents.<br />");
        out.println("For further information refer to the xml schema files or check the Example ");
        out.println("in 'EPC Information Services (EPCIS) Version 1.0 Specification', Section 9.6.</p>");
        out.println("</body>");
        out.println("</html>");

        out.flush();
        out.close();
    }

    /**
     * Invokes the parser (SAX) and catches possible errors. Returns a simple
     * plaintext error messages via HTTP. Note: Currently there is no validation
     * against the EPCglobal schema files, however this application takes care
     * of invalid XML docments.
     */
    public void doPost(final HttpServletRequest req,
            final HttpServletResponse rsp) throws ServletException, IOException {
        LOG.info("EPCIS Capture Interface invoked.");
        rsp.setContentType("text/plain");
        final PrintWriter out = rsp.getWriter();

        try {
            dbconnection = db.getConnection();

            // get POST data
            String event;
            try {
                event = (String) req.getParameterValues("event")[0];
            } catch (final NullPointerException e) {
                throw new IOException(
                        "HTTP POST argument \"event=\" not found in request.");
            }

            // parse the payload into a DOM tree
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            final DocumentBuilder builder = factory.newDocumentBuilder();
            document = builder.parse(new ByteArrayInputStream(event.getBytes()));

            // validate the DOM tree
            if (validator != null) {
                try {
                    validator.validate(new DOMSource(document), null);
                } catch (SAXException e) {
                    String msg = "Unable to validate the document: "
                            + (e.getException() == null
                                    ? e.getMessage()
                                    : e.getException().getMessage());
                    LOG.error(msg, e);
                    rsp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.println(msg);
                }
            }

            // parse the DOM tree
            parseDocument();

            LOG.info("EPCIS Capture Interface request succeeded.");
            rsp.setStatus(HttpServletResponse.SC_OK);
            out.println("Request succeeded.");

        } catch (final SAXException e) {
            String msg = "Unable to parse the document: "
                    + (e.getException() == null
                            ? e.getMessage()
                            : e.getException().getMessage());
            LOG.error(msg, e);
            rsp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.println(msg);

        } catch (final ParserConfigurationException e) {
            String msg = "Parser configuration error: " + e.getMessage();
            LOG.error(msg, e);
            rsp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.println(msg);

        } catch (final IOException e) {
            String msg = "I/O error: " + e.getMessage();
            LOG.error(msg, e);
            rsp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.println(msg);

        } catch (final SQLException e) {
            String msg = "Database error: " + e.getMessage();
            LOG.error(msg, e);
            rsp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.println(msg);

        } catch (final Exception e) {
            String msg = "Unable to complete request due to unknown exception: "
                    + e.getMessage();
            LOG.error(msg, e);
            rsp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.println(msg);

        } finally {
            // close the db connection
            try {
                dbconnection.close();
            } catch (SQLException e) {
                LOG.error("Unable to close DB connection.", e);
            }
        }
    }

    /**
     * @see javax.servlet.GenericServlet#init()
     */
    public void init() throws ServletException {
        // read configuration and set up database source
        try {
            Context initContext = new InitialContext();
            Context env = (Context) initContext.lookup("java:comp/env");
            db = (DataSource) env.lookup("jdbc/EPCISDB");
        } catch (NamingException e) {
            String msg = "Unable to read configuration.";
            LOG.error(msg, e);
            throw new ServletException(msg, e);
        }

        // load parameters
        String servletPath = getServletContext().getRealPath("/");
        String bool = getServletContext().getInitParameter("insertMissingVoc");
        insertMissingVoc = Boolean.parseBoolean(bool);

        // load log4j config
        String log4jConfigFile = getServletContext().getInitParameter(
                "log4jConfigFile");
        if (log4jConfigFile != null) {
            // if no log4j properties file found, then do not try
            // to load it (the application runs without logging)
            PropertyConfigurator.configure(servletPath + log4jConfigFile);
        }

        // load the schema validator
        try {
            String schemaPath = servletPath
                    + getServletContext().getInitParameter("schemaPath");
            String schemaFile = getServletContext().getInitParameter(
                    "schemaFile");
            System.setProperty("user.dir", schemaPath);
            String xsd = schemaPath + System.getProperty("file.separator")
                    + schemaFile;
            LOG.debug("Reading schema from '" + xsd + "'.");

            // load the schema to validate against
            SchemaFactory schemaFact = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Source schemaSrc = new StreamSource(new FileInputStream(xsd));
            Schema schema = schemaFact.newSchema(schemaSrc);

            validator = schema.newValidator();
        } catch (FileNotFoundException e) {
            LOG.warn("Unable to find the schema file (check "
                    + "'pathToSchemaFiles' parameter in META-INF/context.xml)",
                    e);
            LOG.warn("Schema validation will not be available!");
        } catch (Exception e) {
            LOG.warn("Unable to load the schema validator.", e);
            LOG.warn("Schema validation will not be available!");
        }
    }

    /**
     * Parses the entire document and handles the supplied events.
     * 
     * @throws SQLException
     * @throws SAXException
     */
    private void parseDocument() throws SQLException, SAXException {
        NodeList eventList = document.getElementsByTagName("EventList");
        NodeList events = eventList.item(0).getChildNodes();

        // walk through all supplied events
        for (int i = 0; i < events.getLength(); i++) {
            Node eventNode = events.item(i);
            LOG.info("Processing event " + i + " '" + eventNode.getNodeName()
                    + "'.");

            if (eventNode.getNodeName().equals("ObjectEvent")
                    || eventNode.getNodeName().equals("AggregationEvent")
                    || eventNode.getNodeName().equals("QuantityEvent")
                    || eventNode.getNodeName().equals("TransactionEvent")) {
                handleEvent(eventNode);
            } else if (!eventNode.getNodeName().equals("#text")
                    && !eventNode.getNodeName().equals("#comment")) {
                throw new SAXException("Encountered unknown event '"
                        + eventNode.getNodeName() + "'.");
            }
        }
    }

    /**
     * Takes an XML document node, parses it as EPCIS event and inserts the data
     * into the database. The parse routine is generic for all event types; the
     * query generation part has some if/elses to take care of different event
     * parameters.
     * 
     * @param eventNode
     *            The current event node.
     * @throws SAXException
     *             If an error parsing the XML occurs.
     * @throws SQLException
     *             If an error connecting to the DB occurs.
     */
    private void handleEvent(Node eventNode) throws SAXException, SQLException {
        if (eventNode != null && eventNode.getChildNodes().getLength() == 0) {
            throw new SAXException("Event element '" + eventNode.getNodeName()
                    + "' has no children elements.");
        }
        Node curEventNode = null;

        // A lot of the initialized varibles have type URI. This type isn't to
        // compare with the URI-Type of the standard. In fact, most of the
        // Variables having type URI are declared as Vocabularies in the
        // Standard. Commonly, we use String for the Standard-Type URI.

        Timestamp eventTime = null;
        String eventTimeZoneOffset = null;
        String action = null;
        String parentID = null;
        Long quantity = null;
        Vocabulary bizStep = null;
        Vocabulary disposition = null;
        Vocabulary readPoint = null;
        Vocabulary bizLocation = null;
        Vocabulary epcClass = null;

        Vocabulary[] epcs = null;
        List<BusinessTransaction> bizTransactionList = null;
        List<EventFieldExtension> fieldNameExtList = new ArrayList<EventFieldExtension>();

        try {
            for (int i = 0; i < eventNode.getChildNodes().getLength(); i++) {
                curEventNode = eventNode.getChildNodes().item(i);
                String nodeName = curEventNode.getNodeName();
                LOG.info("Handling XML tag: " + nodeName);

                if (nodeName.equals("eventTime")) {
                    String xmlTime = curEventNode.getTextContent();
                    LOG.info("time in xml is '" + xmlTime + "'");
                    try {
                        eventTime = TimeParser.parseAsTimestamp(xmlTime);
                    } catch (ParseException e) {
                        throw new SAXException(
                                "Invalid date/time (must be ISO8601).", e);
                    }
                    LOG.info("time parsed as '" + eventTime + "'");
                } else if (nodeName.equals("eventTimeZoneOffset")) {
                    eventTimeZoneOffset = curEventNode.getTextContent();
                } else if (nodeName.equals("epcList")
                        || nodeName.equals("childEPCs")) {
                    epcs = handleEpcs(curEventNode);
                } else if (nodeName.equals("bizTransactionList")) {
                    bizTransactionList = handleBizTransactions(curEventNode);
                } else if (nodeName.equals("action")) {
                    action = curEventNode.getTextContent();
                    if (!action.equals("ADD") && !action.equals("OBSERVE")
                            && !action.equals("DELETE")) {
                        throw new SAXException(
                                "Encountered illegal \"action\" value: "
                                        + action);
                    }
                } else if (nodeName.equals("bizStep")) {
                    bizStep = new Vocabulary(curEventNode.getTextContent());
                } else if (nodeName.equals("disposition")) {
                    disposition = new Vocabulary(curEventNode.getTextContent());
                } else if (nodeName.equals("readPoint")) {
                    Element attrElem = (Element) curEventNode;
                    Node id = attrElem.getElementsByTagName("id").item(0);
                    readPoint = new Vocabulary(id.getTextContent());
                } else if (nodeName.equals("bizLocation")) {
                    Element attrElem = (Element) curEventNode;
                    Node id = attrElem.getElementsByTagName("id").item(0);
                    bizLocation = new Vocabulary(id.getTextContent());
                } else if (nodeName.equals("epcClass")) {
                    epcClass = new Vocabulary(curEventNode.getTextContent());
                } else if (nodeName.equals("quantity")) {
                    quantity = new Long(curEventNode.getTextContent());
                } else if (nodeName.equals("parentID")) {
                    parentID = curEventNode.getTextContent();
                } else {
                    if (!nodeName.equals("#text")
                            && !nodeName.equals("#comment")) {

                        String[] parts = nodeName.split(":");
                        if (parts.length == 2) {
                            LOG.info("Treating unknown XML tag '" + nodeName
                                    + "' as extension.");
                            String prefix = parts[0];
                            String localname = parts[1];
                            String namespace = document.getDocumentElement().getAttribute(
                                    "xmlns:" + prefix);
                            String value = curEventNode.getTextContent();
                            fieldNameExtList.add(new EventFieldExtension(
                                    prefix, namespace, localname, value));
                        } else {
                            // this is not a valid extension
                            throw new SAXException(
                                    "Encountered unknown XML tag '" + nodeName
                                            + "'.");
                        }
                    }
                }
            }
        } catch (final VociSyntaxException e) {
            throw new SAXException("'" + curEventNode.getNodeName()
                    + "' is not of type URI: " + curEventNode.getTextContent(),
                    e);
        }

        // preparing query
        PreparedStatement ps;
        String nodeName = eventNode.getNodeName();
        if (nodeName.equals("AggregationEvent")) {
            ps = dbconnection.prepareStatement(aggregationEventInsert);
        } else if (nodeName.equals("ObjectEvent")) {
            ps = dbconnection.prepareStatement(objectEventInsert);
        } else if (nodeName.equals("QuantityEvent")) {
            ps = dbconnection.prepareStatement(quantityEventInsert);
        } else if (nodeName.equals("TransactionEvent")) {
            ps = dbconnection.prepareStatement(transactionEventInsert);
        } else {
            throw new SAXException("Encountered unknown event element '"
                    + nodeName + "'.");
        }

        // parameters 1-7 of the sql query are shared by all events
        ps.setTimestamp(1, eventTime);
        ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
        ps.setString(3, eventTimeZoneOffset);
        if (bizStep != null) {
            ps.setLong(4, insertVocabulary("voc_BizStep", bizStep));
        } else {
            ps.setNull(4, java.sql.Types.BIGINT);
        }
        if (disposition != null) {
            ps.setLong(5, insertVocabulary("voc_Disposition", disposition));
        } else {
            ps.setNull(5, java.sql.Types.BIGINT);
        }
        if (readPoint != null) {
            ps.setLong(6, insertVocabulary("voc_ReadPoint", readPoint));
        } else {
            ps.setNull(6, java.sql.Types.BIGINT);
        }
        if (bizLocation != null) {
            ps.setLong(7, insertVocabulary("voc_BizLoc", bizLocation));
        } else {
            ps.setNull(7, java.sql.Types.BIGINT);
        }

        // special handling for QuantityEvent
        if (nodeName.equals("QuantityEvent")) {
            if (epcClass != null) {
                ps.setLong(8, insertVocabulary("voc_EPCClass", epcClass));
            } else {
                ps.setNull(8, java.sql.Types.BIGINT);
            }
            if (quantity != null) {
                ps.setLong(9, quantity.longValue());
            } else {
                ps.setNull(9, java.sql.Types.BIGINT);
            }
        } else {
            // all other events have action
            ps.setString(8, action);

            // AggregationEvent has additional field parentID
            if (nodeName.equals("AggregationEvent")) {
                ps.setString(9, parentID);
            }
        }

        // insert event into database
        ps.executeUpdate();

        long eventId = getLastAutoIncrementedId("event_" + nodeName);
        if (!fieldNameExtList.isEmpty()) {
            for (EventFieldExtension ext : fieldNameExtList) {

                /* preparing statement for insertion of associated EPCs */
                ps = dbconnection.prepareStatement("INSERT INTO event_"
                        + eventNode.getNodeName() + "_extensions "
                        + "(event_id, fieldname, prefix, "
                        + ext.getValueColumnName() + ") VALUES (?, ? ,?, ?)");

                ps.setLong(1, eventId);
                ps.setString(2, ext.getFieldname());
                ps.setString(3, ext.getPrefix());
                if (ext.getIntValue() != null) {
                    ps.setInt(4, ext.getIntValue());
                } else if (ext.getFloatValue() != null) {
                    ps.setFloat(4, ext.getFloatValue());
                } else if (ext.getDateValue() != null) {
                    ps.setTimestamp(4, ext.getDateValue());
                } else {
                    ps.setString(4, ext.getStrValue());
                }

                ps.executeUpdate();
            }
        }

        // check if the event has any EPCs
        if (epcs != null && !nodeName.equals("QuantityEvent")) {
            // preparing statement for insertion of associated EPCs
            String stmt = "INSERT INTO event_" + nodeName
                    + "_EPCs (event_id, epc) VALUES (?, ?)";
            ps = dbconnection.prepareStatement(stmt);
            ps.setLong(1, eventId);

            // insert all EPCs in the EPCs array
            for (int i = 0; i < epcs.length; i++) {
                ps.setString(2, epcs[i].toString());
                ps.executeUpdate();
            }
        }

        // check if the event has any bizTransactions
        if (bizTransactionList != null) {
            // preparing statement for insertion of associated EPCs
            String stmt = "INSERT INTO event_" + nodeName
                    + "_bizTrans (event_id, bizTrans_id) VALUES (?, ?)";
            ps = dbconnection.prepareStatement(stmt);
            ps.setLong(1, eventId);

            // insert all BizTransactions into the BusinessTransaction-Table
            // and connect it with the "event_<event-name>_bizTrans"-Table
            for (final BusinessTransaction bizTrans : bizTransactionList) {
                ps.setLong(2, insertBusinessTransaction(bizTrans));
                ps.executeUpdate();
            }
        }
    }

    /**
     * Retrieves the last inserted ID chosen by the autoIncrement functionality
     * in the table with the given name.
     * 
     * @param tableName
     *            The name of the table for which the last inserted ID should be
     *            retrieved.
     * @return The last auto incremented ID.
     * @throws SQLException
     *             If an SQL problem with the database ocurred.
     */
    private long getLastAutoIncrementedId(String tableName) throws SQLException {
        String stmt = "SELECT LAST_INSERT_ID() as id " + "FROM " + tableName;
        PreparedStatement ps = dbconnection.prepareStatement(stmt);
        final ResultSet rs = ps.executeQuery();
        rs.next();
        return rs.getLong("id");
    }

    /**
     * Inserts vocabulary into the database by searching for already existing
     * entries; if found, the corresponding ID is returned. If not found, the
     * vocabulary is extended if "insertmissingvoc" is true; otherwise an
     * SQLException is thrown
     * 
     * @param tableName
     *            The name of the vocabulary table.
     * @param uri
     *            The Voci adapting the URI to be inserted into the vocabulary
     *            table.
     * @return The ID of an already existing vocabulary table with the given
     *         uri.
     * @throws SQLException
     *             If an SQL problem with the database ocurred or if we are not
     *             allowed to insert a missing vocabulary.
     */
    private long insertVocabulary(String tableName, Vocabulary uri)
            throws SQLException {
        String stmt = "SELECT id FROM " + tableName + " WHERE uri=?";
        PreparedStatement ps = dbconnection.prepareStatement(stmt);
        ps.setString(1, uri.toString());
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            // the uri already exists
            return rs.getLong("id");
        } else {
            // the uri does not yet exist: insert it if allowed. According to
            // the specs, some vocabulary is not allowed to be extended; this is
            // currently ignored here
            if (insertMissingVoc) {
                stmt = "INSERT INTO " + tableName + " (uri) VALUES (?)";
                ps = dbconnection.prepareStatement(stmt);
                ps.setString(1, uri.toString());
                ps.executeUpdate();

                // get last auto_increment value and return it
                return getLastAutoIncrementedId(tableName);
            } else {
                throw new SQLException(
                        "Not allowed to add new vocabulary - use "
                                + "existing vocabulary");
            }
        }
    }

    /**
     * Parses the xml tree for epc nodes and returns a list of EPC URIs.
     * 
     * @param epcNode
     *            The parent Node from which EPC URIs should be extracted.
     * @return An array of Voci containing all the URIs found in the given node.
     * @throws URISyntaxException
     *             If a string is not parsable as URI.
     * @throws SAXParseException
     *             If an unknown tag (no &lt;epc&gt;) is encountered.
     */
    private Vocabulary[] handleEpcs(final Node epcNode)
            throws VociSyntaxException, SAXParseException {
        // write EPCs into a list first, because we cannot yet safely say how
        // many EPCs there will be
        List<Vocabulary> epcList = new ArrayList<Vocabulary>();

        for (int i = 0; i < epcNode.getChildNodes().getLength(); i++) {
            Node curNode = epcNode.getChildNodes().item(i);
            if (curNode.getNodeName().equals("epc")) {
                epcList.add(new Vocabulary(curNode.getTextContent()));
            } else {
                if (curNode.getNodeName() != "#text"
                        && curNode.getNodeName() != "#comment") {
                    throw new SAXParseException("Unknown XML tag: "
                            + curNode.getNodeName(), null);
                }
            }
        }

        Vocabulary[] epcs = new Vocabulary[epcList.size()];
        epcList.toArray(epcs);

        return epcs;
    }

    /**
     * Parses the xml tree for epc nodes and returns a List of BizTransaction
     * URIs with their corresponing type.
     * 
     * @param bizNode
     *            The parent Node from which BizTransaction URIs should be
     *            extracted.
     * @return A List of BizTransaction.
     * @throws URISyntaxException
     *             If a string is not parsable as URI.
     * @throws SAXParseException
     *             If an unknown tag (no &lt;epc&gt;) is encountered.
     */
    private List<BusinessTransaction> handleBizTransactions(final Node bizNode)
            throws VociSyntaxException, SAXParseException {
        // write EPCs into a list first, because we cannot yet safely say how
        // many EPCs there will be
        final List<BusinessTransaction> bizList = new ArrayList<BusinessTransaction>();

        for (int i = 0; i < bizNode.getChildNodes().getLength(); i++) {
            Node curNode = bizNode.getChildNodes().item(i);
            if (curNode.getNodeName().equals("bizTransaction")) {
                bizList.add(new BusinessTransaction(new Vocabulary(
                        curNode.getAttributes().item(0).getTextContent()),
                        new Vocabulary(curNode.getTextContent())));
            } else {
                if (!curNode.getNodeName().equals("#text")
                        && !curNode.getNodeName().equals("#comment")) {
                    throw new SAXParseException("Unknown XML tag: "
                            + curNode.getNodeName(), null);
                }
            }
        }
        return bizList;
    }

    /**
     * Inserts the BusinessTransactionType and the BusinessTransactionID into
     * the BusinessTransaction-Table if necessary.
     * 
     * @param bizTrans
     *            The BusinessTransaction to be inserted.
     * @return The ID from the BusinessTransaction-table.
     * @throws SQLException
     *             If an SQL problem with the database ocurred.
     */
    private Long insertBusinessTransaction(final BusinessTransaction bizTrans)
            throws SQLException {
        final long id = insertVocabulary("voc_BizTrans",
                bizTrans.getBizTransID());
        final long type = insertVocabulary("voc_BizTransType",
                bizTrans.getBizTransType());

        String stmt = "SELECT id FROM BizTransaction WHERE bizTrans=? AND type=?";
        PreparedStatement ps = dbconnection.prepareStatement(stmt);
        ps.setLong(1, id);
        ps.setLong(2, type);

        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            // the BusinessTransaction already exists
            return rs.getLong("id");
        } else {
            // insert the BusinessTransaction
            stmt = "INSERT INTO BizTransaction (bizTrans, type) VALUES (?, ?)";
            ps = dbconnection.prepareStatement(stmt);
            ps.setLong(1, id);
            ps.setLong(2, type);
            ps.executeUpdate();

            return getLastAutoIncrementedId("BizTransaction");
        }
    }
}