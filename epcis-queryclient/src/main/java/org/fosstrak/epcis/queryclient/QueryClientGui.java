package org.accada.epcis.queryclient;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;

import org.accada.epcis.soapapi.ArrayOfString;
import org.accada.epcis.soapapi.QueryParam;
import org.accada.epcis.soapapi.QuerySchedule;
import org.accada.epcis.soapapi.Subscribe;
import org.accada.epcis.soapapi.SubscriptionControls;
import org.apache.axis.AxisFault;
import org.apache.axis.types.URI;

/**
 * Implements the GUI part of the EPCIS Query Interface client.
 * @author David Gubler
 *
 */
public class QueryClientGui
        extends WindowAdapter
        implements ActionListener {


    /**
     * The enumaration of all possible Types a Queryparameter can have.
     */
    public enum ParameterType {
        ListOfString, Boolean, Int, Float, String, Time, noType
    };

    /**
     * The HasMap wich holds all possible queryParameters.
     * Key is the UserText.
     */
    LinkedHashMap<String, QueryItem> queryParamsUserText;

    /**
     * The HasMap wich holds all possible queryParameters.
     * Key is the QueryText.
     */
    LinkedHashMap<String, QueryItem> queryParamsQueryText;

    /**
     * Contains the column names for the result table.
     */
    private final String[] columnNames = {
            "Event",
            "occured",
            "recorded",
            "Parent ID",
            "Quantity",
            "EPCs",
            "Action",
            "Business step",
            "Disposition",
            "Readpoint ID",
            "Business location",
            "Business transaction"
    };

    /**
     * Contains the various choices for the query parameterers in a human
     * readable form.
     */
    private String[] queryParameterUsertext;

    /**
     * Contains the data for the result table.
     */
    private Object[][] data = {};

    /**
     * ISO 8601 SimpleDateFormat.
     * Use it like this for current time: isoDateFormat.format(now)
     */
    private final SimpleDateFormat isoDateFormat =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

    /**
     * The default URL for the service.
     */
    private String defaultUrl =
            "http://demo.accada.org/EPCIS-Query-v0.2.0";

    /**
     * The query client instance. Has methods to actually execute a query.
     */
    private QueryClient client = null;

    /**
     * All the examples are contained in an ExampleQueries instance.
     */
    private QueryClientGuiExampleQueries exampleQueries =
            new QueryClientGuiExampleQueries();

    /* main window */
    private JFrame mainWindow;

    private JPanel mwMainPanel;
    private JPanel mwConfigPanel;
    private JPanel mwSubscribeManagementPanel;
    private JPanel mwEventTypeSelectPanel;
    private JPanel mwQueryPanel;
    private JPanel mwSubscriptionPanel;
    private JPanel mwQueryArgsPanel;
    private JPanel mwQueryExamplesPanel;
    private JPanel mwButtonPanel;

    private JLabel mwServiceUrlLabel;
    private JTextField mwServiceUrlTextField;
    private JButton mwServiceInfoButton;

    private JLabel mwUnsubscribeQueryLabel;
    private JTextField mwUnsubscribeQueryTextField;
    private JButton mwUnsubscribeQueryButton;
    private JButton mwSubscriptionIdButton;

    private JCheckBox mwShowDebugWindowCheckBox;
    private JCheckBox mwObjectEventsCheckBox;
    private JCheckBox mwAggregationEventsCheckBox;
    private JCheckBox mwQuantityEventsCheckBox;
    private JCheckBox mwTransactionEventsCheckBox;

    /* These lists hold the input fields for the query arguments.
     * The lists are modified by the user to allow for as many
     * arguments as the user wants
     * Objects may be deleted from these lists by selecting "ignore"
     * from the drop-down box
     */
    private LinkedList<JComboBox> mwQuerySelectComboBoxes;
    private LinkedList<JTextFieldEnhanced> mwQueryArgumentTextFields;

    private int mwQueryArgumentTextFieldsExtraWidth = 550;
    private int mwHeightDifference;

    private JButton mwRunQueryButton;
    private JButton mwFillInExampleButton;

    /* subscribe Query */
    private JCheckBox isSubscribed;
    private JTextField mwScheduleMinuteField;
    private JTextField mwScheduleSecField;
    private JTextField mwScheduleHourField;
    private JTextField mwScheduleWeekField;
    private JTextField mwScheduleMonthField;
    private JTextField mwScheduleDayField;
    private JTextField mwSubIdField;
    private JTextField mwInitRecTimeField;
    private JTextField mwDestUriTextField;
    private JCheckBox reportIf;

    /* results window */
    private JFrame resultsWindow;

    private JPanel rwResultsPanel;
    private JTable rwResultsTable;
    private JScrollPane rwResultsScrollPane;

    /* example selection window */
    private JFrame exampleWindow;

    private JPanel ewMainPanel;
    private JPanel ewListPanel;
    private JPanel ewButtonPanel;
    private JList ewExampleList;
    private JScrollPane ewExampleScrollPane;
    private JButton ewOkButton;

    /* debug window */
    private JFrame debugWindow;

    private JTextArea dwOutputTextArea;
    private JScrollPane dwOutputScrollPane;
    private JPanel dwButtonPanel;
    private JButton dwClearButton;

    /**
     * The constructor. Starts a new thread which draws the main window.
     *
     */
    public QueryClientGui() {
        initWindow();
    }
    
    public QueryClientGui(String address) {
        this.defaultUrl = address;
        initWindow();
    }
    
    private void initWindow() {
        generateParamHashMap();
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createMainWindow();
            }
        });
    }

    /**
     * Initialized all the possible Query Parameters.
     */
    private void generateParamHashMap() {
        QueryItem newEntry = new QueryItem();
        queryParamsUserText = new LinkedHashMap<String, QueryItem>();
        queryParamsQueryText = new LinkedHashMap<String, QueryItem>();

        newEntry.setDescription("Choose a query parameter "
                + "from the drop-down menu");
        newEntry.setParamType(ParameterType.noType);
        newEntry.setQueryText("");
        newEntry.setRequired(false);
        newEntry.setUserText("ignore");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Format is ISO 8601, i.e. YYYY-MM-DDThh:mm:ss.sss");
        newEntry.setParamType(ParameterType.Time);
        newEntry.setQueryText("GE_eventTime");
        newEntry.setRequired(false);
        newEntry.setUserText("event time >= ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Format is ISO 8601, i.e. YYYY-MM-DDThh:mm:ss.sss");
        newEntry.setParamType(ParameterType.Time);
        newEntry.setQueryText("LT_eventTime");
        newEntry.setRequired(false);
        newEntry.setUserText("event time < ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Format is ISO 8601, i.e. YYYY-MM-DDThh:mm:ss.sss");
        newEntry.setParamType(ParameterType.Time);
        newEntry.setQueryText("GE_recordTime");
        newEntry.setRequired(false);
        newEntry.setUserText("record time >= ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Format is ISO 8601, i.e. YYYY-MM-DDThh:mm:ss.sss");
        newEntry.setParamType(ParameterType.Time);
        newEntry.setQueryText("LT_recordTime");
        newEntry.setRequired(false);
        newEntry.setUserText("record time < ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Space-separated list of ADD, DELETE, OBSERVE");
        newEntry.setParamType(ParameterType.ListOfString);
        newEntry.setQueryText("EQ_action");
        newEntry.setRequired(false);
        newEntry.setUserText("action = ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Space-separated list "
                + "of URIs with OR semantics");
        newEntry.setParamType(ParameterType.ListOfString);
        newEntry.setQueryText("EQ_bizStep");
        newEntry.setRequired(false);
        newEntry.setUserText("business step = ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Space-separated list "
                + "of URIs with OR semantics");
        newEntry.setParamType(ParameterType.ListOfString);
        newEntry.setQueryText("EQ_disposition");
        newEntry.setRequired(false);
        newEntry.setUserText("disposition = ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Space-separated list "
                + "of URIs with OR semantics");
        newEntry.setParamType(ParameterType.ListOfString);
        newEntry.setQueryText("EQ_readPoint");
        newEntry.setRequired(false);
        newEntry.setUserText("readpoint = ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Space-separated list "
                + "of URIs with OR semantics");
        newEntry.setParamType(ParameterType.ListOfString);
        newEntry.setQueryText("WD_readPoint");
        newEntry.setRequired(false);
        newEntry.setUserText("readpoint descendant of ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Space-separated list "
                + "of URIs with OR semantics");
        newEntry.setParamType(ParameterType.ListOfString);
        newEntry.setQueryText("EQ_bizLocation");
        newEntry.setRequired(false);
        newEntry.setUserText("business location = ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Space-separated list "
                + "of URIs with OR semantics");
        newEntry.setParamType(ParameterType.ListOfString);
        newEntry.setQueryText("WD_bizLocation");
        newEntry.setRequired(false);
        newEntry.setUserText("business location descendant of ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        // "EQ_bizTransaction_type", "business transaction type with ID's= ",
        //          we do not support this in the GUI (List of String)

        newEntry = new QueryItem();
        newEntry.setDescription("Space-separated list "
                + "of URIs with OR semantics");
        newEntry.setParamType(ParameterType.ListOfString);
        newEntry.setQueryText("MATCH_epc");
        newEntry.setRequired(false);
        newEntry.setUserText("EPC = ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Space-separated list "
                + "of URIs with OR semantics");
        newEntry.setParamType(ParameterType.ListOfString);
        newEntry.setQueryText("MATCH_parentID");
        newEntry.setRequired(false);
        newEntry.setUserText("parent ID = ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Space-separated list "
                + "of URIs with OR semantics");
        newEntry.setParamType(ParameterType.ListOfString);
        newEntry.setQueryText("MATCH_childEPC");
        newEntry.setRequired(false);
        newEntry.setUserText("child EPC = ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Space-separated list "
                + "of URIs with OR semantics");
        newEntry.setParamType(ParameterType.ListOfString);
        newEntry.setQueryText("MATCH_epcClass");
        newEntry.setRequired(false);
        newEntry.setUserText("EPC class = ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Integer number");
        newEntry.setParamType(ParameterType.Int);
        newEntry.setQueryText("EQ_quantity");
        newEntry.setRequired(false);
        newEntry.setUserText("quantity = ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Integer number");
        newEntry.setParamType(ParameterType.Int);
        newEntry.setQueryText("GT_quantity");
        newEntry.setRequired(false);
        newEntry.setUserText("quantity > ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Integer number");
        newEntry.setParamType(ParameterType.Int);
        newEntry.setQueryText("GE_quantity");
        newEntry.setRequired(false);
        newEntry.setUserText("quantity >= ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Integer number");
        newEntry.setParamType(ParameterType.Int);
        newEntry.setQueryText("LT_quantity");
        newEntry.setRequired(false);
        newEntry.setUserText("quantity < ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Integer number");
        newEntry.setParamType(ParameterType.Int);
        newEntry.setQueryText("LE_quantity");
        newEntry.setRequired(false);
        newEntry.setUserText("quantity <= ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        // "EQ_fieldname", "Fieldname with values = "
        //          we do not support this in the GUI (List of String)
        // "EQ_fieldname", "Fieldname with values = "
        //          we do not support this in the GUI (Int, Float, Time)
        // "GT_fieldname", "Fieldname with values > "
        //          we do not support this in the GUI (Int, Float, Time)
        // "GE_fieldname", "Fieldname with values >= "
        //          we do not support this in the GUI (Int, Float, Time)
        // "LT_fieldname", "Fieldname with values < "
        //          we do not support this in the GUI (Int, Float, Time)
        // "LE_fieldname", "Fieldname with values <= "
        //          we do not support this in the GUI (Int, Float, Time)
        // "EXISTS_fieldname",  "exists field: "
        //          we do not support this in the GUI (Void)
        // "HASATTR_fieldname", "Has fieldname attributes: "
        //          we do not support this in the GUI (List of String)
        // "EQATTR_fieldname_attrname", "Equals fieldname attributname: "
        //          we do not support this in the GUI (List of String)

        newEntry = new QueryItem();
        newEntry.setDescription("A single fieldname written in");
        newEntry.setParamType(ParameterType.String);
        newEntry.setQueryText("orderBy");
        newEntry.setRequired(false);
        newEntry.setUserText("Order by field: ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("ASC or DESC. Default is DESC.");
        newEntry.setParamType(ParameterType.String);
        newEntry.setQueryText("orderDirection");
        newEntry.setRequired(false);
        newEntry.setUserText("direction of order: ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Integer number");
        newEntry.setParamType(ParameterType.Int);
        newEntry.setQueryText("eventCountLimit");
        newEntry.setRequired(false);
        newEntry.setUserText("only the first n: ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        newEntry = new QueryItem();
        newEntry.setDescription("Integer number");
        newEntry.setParamType(ParameterType.Int);
        newEntry.setQueryText("maxEventCount");
        newEntry.setRequired(false);
        newEntry.setUserText("has not more then n Events ");
        queryParamsUserText.put(newEntry.getUserText(), newEntry);
        queryParamsQueryText.put(newEntry.getQueryText(), newEntry);

        queryParameterUsertext = new String[queryParamsUserText.size()];
        int i = 0;
        for (Iterator it = queryParamsUserText.keySet().iterator(); it.hasNext(); ) {
            queryParameterUsertext[i] = (String)it.next();
            i++;
       }
    }


    /**
     * Set a query client.
     * @param newclient
     */
    public void setQueryClient(final QueryClient newclient) {
        client = newclient;
    }

    /**
     * Sets up the main window.
     * To be called only once per program run
     *
     */
    private void createMainWindow() {
        JFrame.setDefaultLookAndFeelDecorated(true);

        mainWindow = new JFrame("EPCIS query interface client");
        mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainWindow.setResizable(false);

        mwMainPanel = new JPanel();
        mwMainPanel
                .setLayout(new BoxLayout(mwMainPanel, BoxLayout.PAGE_AXIS));
        mwMainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        mwConfigPanel = new JPanel(new GridBagLayout());
        mwMainPanel.add(mwConfigPanel);
        
        mwSubscribeManagementPanel = new JPanel(new GridBagLayout());
        mwMainPanel.add(mwSubscribeManagementPanel);

        mwEventTypeSelectPanel = new JPanel();
        mwMainPanel.add(mwEventTypeSelectPanel);

        mwQueryPanel = new JPanel();
        mwSubscriptionPanel = new JPanel();
        mwQueryPanel.setLayout(
                new BoxLayout(mwQueryPanel, BoxLayout.PAGE_AXIS));
        mwSubscriptionPanel.setLayout(new GridBagLayout());
        mwMainPanel.add(mwQueryPanel);

        isSubscribed = new JCheckBox("Subscribe this query");
        mwMainPanel.add(isSubscribed);
        isSubscribed.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (mwSubscriptionPanel.isVisible()) {
                    mwSubscriptionPanel.setVisible(false);
                    mwRunQueryButton.setText("Run Query");
                    mainWindow.pack();
                } else {
                    mwSubscriptionPanel.setVisible(true);
                    mwRunQueryButton.setText("Subscribe Query");
                    mainWindow.pack();
                }
            }
        });
        mwSubscriptionPanel.setVisible(false);
        mwMainPanel.add(mwSubscriptionPanel);

        mwButtonPanel = new JPanel();
        mwMainPanel.add(mwButtonPanel);

        mwConfigPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Configuration"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        mwSubscribeManagementPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Subscribe Management"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        mwEventTypeSelectPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Events to be returned"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        mwQueryPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Query arguments"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        mwSubscriptionPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Subscription Arguments"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));


        mwServiceUrlLabel = new JLabel("Query interface URL: ");
        mwServiceUrlTextField = new JTextField(defaultUrl, 40);
        mwServiceInfoButton = new JButton("Info");
        mwServiceInfoButton.addActionListener(this);

        mwUnsubscribeQueryLabel = new JLabel("Unsubscribe ID: ");
        mwUnsubscribeQueryTextField = new JTextField("", 40);
        mwUnsubscribeQueryTextField.setToolTipText("Only one Subscription ID");
        mwUnsubscribeQueryButton = new JButton("Unsubscribe");
        mwUnsubscribeQueryButton.addActionListener(this);
        mwSubscriptionIdButton = new JButton("Show SubscriptionIDs");
        mwSubscriptionIdButton.addActionListener(this);

        mwShowDebugWindowCheckBox = new JCheckBox("Show debug window", false);
        mwShowDebugWindowCheckBox.addActionListener(this);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5,5,5,0);
        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 0;
        mwConfigPanel.add(mwServiceUrlLabel, c);
        c.weightx = 1;
        c.gridx = 1;
        c.gridy = 0;
        mwConfigPanel.add(mwServiceUrlTextField, c);
        c.weightx = 0;
        c.gridx = 3;
        c.gridy = 0;
        mwConfigPanel.add(mwServiceInfoButton, c);
        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 2;
        mwConfigPanel.add(mwShowDebugWindowCheckBox, c);


        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;
        mwSubscribeManagementPanel.add(mwUnsubscribeQueryLabel, c);
        c.weightx = 1;
        c.gridx = 1;
        c.gridy = 0;
        mwSubscribeManagementPanel.add(mwUnsubscribeQueryTextField, c);
        c.weightx = 0;
        c.gridx = 2;
        c.gridy = 0;
        mwSubscribeManagementPanel.add(mwUnsubscribeQueryButton, c);
        c.weightx = 0;
        c.gridx = 3;
        c.gridy = 0;
        mwSubscribeManagementPanel.add(mwSubscriptionIdButton, c);


        mwObjectEventsCheckBox = new JCheckBox("ObjectEvents");
        mwEventTypeSelectPanel.add(mwObjectEventsCheckBox);
        mwAggregationEventsCheckBox = new JCheckBox("AggregationEvents");
        mwEventTypeSelectPanel.add(mwAggregationEventsCheckBox);
        mwQuantityEventsCheckBox = new JCheckBox("QuantityEvents");
        mwEventTypeSelectPanel.add(mwQuantityEventsCheckBox);
        mwTransactionEventsCheckBox = new JCheckBox("TransactionEvents");
        mwEventTypeSelectPanel.add(mwTransactionEventsCheckBox);

        mwQuerySelectComboBoxes = new LinkedList<JComboBox>();
        mwQueryArgumentTextFields = new LinkedList<JTextFieldEnhanced>();

        mwQuerySelectComboBoxes.add(new JComboBox(queryParameterUsertext));
        ((JComboBox) mwQuerySelectComboBoxes.getFirst())
                .addActionListener(this);
        queryParamsUserText.get("ignore");
        mwQueryArgumentTextFields.add(new JTextFieldEnhanced(15,
                queryParamsUserText.get("ignore")));

        mwQueryArgsPanel = new JPanel(new GridBagLayout());
        mwQueryExamplesPanel = new JPanel(new BorderLayout());
        mwQueryPanel.add(mwQueryArgsPanel);
        mwQueryPanel.add(mwQueryExamplesPanel);

        c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10,5,5,0);
        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 0;
        mwQueryArgsPanel.add(
                (JComboBox) mwQuerySelectComboBoxes.getFirst(), c);
        c.weightx = 1;
        c.gridx = 1;
        c.ipadx = mwQueryArgumentTextFieldsExtraWidth;
        mwQueryArgsPanel.add(
                (JTextField) mwQueryArgumentTextFields.getFirst(), c);




        c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10,5,5,0);
        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 0;
        reportIf = new JCheckBox("Report if empty?");
        reportIf.setSelected(true);
        mwSubscriptionPanel.add(reportIf);

        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 1;
        JLabel mwDestUri = new JLabel("Destination URI: ");
        mwSubscriptionPanel.add(mwDestUri, c);
        c.weightx = 1;
        c.gridx = 1;
        c.gridy = 1;
        c.gridwidth = 6;
        mwDestUriTextField = new JTextField("http://localhost:8888", 40);
        mwSubscriptionPanel.add(mwDestUriTextField, c);

        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 1;
        JLabel mwInitRecTime = new JLabel("Initial Record Time: ");
        mwSubscriptionPanel.add(mwInitRecTime, c);
        c.weightx = 1;
        c.gridx = 1;
        c.gridy = 2;
        c.gridwidth = 6;
        Date now = new Date();
        SimpleDateFormat dateTime =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        mwInitRecTimeField = new JTextField(dateTime.format(now), 40);
        mwSubscriptionPanel.add(mwInitRecTimeField, c);

        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 1;
        JLabel mwSubId = new JLabel("Subscription ID: ");
        mwSubscriptionPanel.add(mwSubId, c);
        c.weightx = 1;
        c.gridx = 1;
        c.gridy = 3;
        c.gridwidth = 6;
        mwSubIdField = new JTextField("", 40);
        mwSubscriptionPanel.add(mwSubIdField, c);

        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 1;
        JLabel mwSchedule = new JLabel("Schedule: ");
        mwSubscriptionPanel.add(mwSchedule, c);

        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 1;
        JLabel mwScheduleDay = new JLabel("Day of Month: ");
        mwSubscriptionPanel.add(mwScheduleDay, c);

        c.gridx = 2;
        c.insets = new Insets(10, 5, 5, 30);
        mwScheduleDayField = new JTextField("", 10);
        mwSubscriptionPanel.add(mwScheduleDayField, c);

        c.gridx = 3;
        c.insets = new Insets(10, 5, 5, 0);
        JLabel mwScheduleMonth = new JLabel("Mont: ");
        mwSubscriptionPanel.add(mwScheduleMonth, c);

        c.gridx = 4;
        c.insets = new Insets(10, 5, 5, 30);
        mwScheduleMonthField = new JTextField("", 10);
        mwSubscriptionPanel.add(mwScheduleMonthField, c);

        c.gridx = 5;
        c.insets = new Insets(10, 5, 5, 0);
        JLabel mwScheduleWeek = new JLabel("Day of Week: ");
        mwSubscriptionPanel.add(mwScheduleWeek, c);

        c.gridx = 6;
        mwScheduleWeekField = new JTextField("", 10);
        mwSubscriptionPanel.add(mwScheduleWeekField, c);

        c.gridy = 5;
        c.gridx = 1;
        c.insets = new Insets(10, 5, 5, 0);
        JLabel mwScheduleHour = new JLabel("Hour: ");
        mwSubscriptionPanel.add(mwScheduleHour, c);

        c.gridx = 2;
        c.insets = new Insets(10, 5, 5, 30);
        mwScheduleHourField = new JTextField("", 10);
        mwSubscriptionPanel.add(mwScheduleHourField, c);

        c.gridx = 3;
        c.insets = new Insets(10, 5, 5, 0);
        JLabel mwScheduleMinute = new JLabel("Minute: ");
        mwSubscriptionPanel.add(mwScheduleMinute, c);

        c.gridx = 4;
        c.insets = new Insets(10, 5, 5, 30);
        mwScheduleMinuteField = new JTextField("", 10);
        mwSubscriptionPanel.add(mwScheduleMinuteField, c);

        c.gridx = 5;
        c.insets = new Insets(10, 5, 5, 0);
        JLabel mwScheduleSec = new JLabel("Sec: ");
        mwSubscriptionPanel.add(mwScheduleSec, c);

        c.gridx = 6;
        mwScheduleSecField = new JTextField("", 10);
        mwSubscriptionPanel.add(mwScheduleSecField, c);



        mwFillInExampleButton = new JButton("Fill in example");
        mwFillInExampleButton.addActionListener(this);
        mwQueryExamplesPanel.add(mwFillInExampleButton, BorderLayout.EAST);

        mwRunQueryButton = new JButton("Run query");
        mwRunQueryButton.addActionListener(this);
        mwButtonPanel.add(mwRunQueryButton);

        mainWindow.getContentPane().add(mwMainPanel);
        mainWindow.pack();
        mainWindow.setVisible(true);

        /* set up debug window */
        createDebugWindow();
        /*
         * set up query client. The supplied JTextArea is used for debug output
         */
        client = new QueryClient(defaultUrl, dwOutputTextArea);

        /* Find out how much the window has to be scaled whenever new
         * components are added. This must be done after rendering the
         * GUI, otherwise the sizes will be wrong!
         */
        if (((JComboBox) mwQuerySelectComboBoxes.getFirst())
                    .getSize().height
            > ((JTextField) mwQueryArgumentTextFields.getFirst())
                    .getSize().height) {
            mwHeightDifference = ((JComboBox) mwQuerySelectComboBoxes
                    .getFirst()).getPreferredSize().height
                    + c.insets.top + c.insets.bottom;
        } else {
            mwHeightDifference = ((JTextField) mwQueryArgumentTextFields
                    .getFirst()).getPreferredSize().height
                    + c.insets.top + c.insets.bottom;
        }
    }

    /**
     * Sets up the window used for results display.
     * Does not destroy the old window.
     *
     */
    private void createResultsWindow() {
        resultsWindow = new JFrame("Query results");
        resultsWindow.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        rwResultsPanel = new JPanel();
        rwResultsPanel.setLayout(
                new BoxLayout(rwResultsPanel, BoxLayout.Y_AXIS));

        rwResultsTable = new JTable(data, columnNames);
        rwResultsScrollPane = new JScrollPane(rwResultsTable);
        rwResultsPanel.add(rwResultsTable.getTableHeader());
        rwResultsPanel.add(rwResultsScrollPane);

        resultsWindow.getContentPane().add(rwResultsPanel);
        resultsWindow.pack();
        resultsWindow.setVisible(true);
    }

    /**
     * Sets up the window used to show the list of examples.
     * Can only be open once.
     *
     */
    private void createExampleWindow() {
        if (exampleWindow != null) {
            exampleWindow.setVisible(true);
            return;
        }
        exampleWindow = new JFrame("Choose example");
        exampleWindow.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        ewMainPanel = new JPanel();
        ewMainPanel.setLayout(new BoxLayout(ewMainPanel, BoxLayout.PAGE_AXIS));
        exampleWindow.add(ewMainPanel);

        ewListPanel = new JPanel();
        ewListPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 5));
        ewListPanel.setLayout(new BoxLayout(ewListPanel, BoxLayout.PAGE_AXIS));

        ewButtonPanel = new JPanel();
        ewButtonPanel.setBorder(
                BorderFactory.createEmptyBorder(5, 10, 10, 10));
        ewButtonPanel.setLayout(
                new BoxLayout(ewButtonPanel, BoxLayout.LINE_AXIS));

        ewMainPanel.add(ewListPanel);
        ewMainPanel.add(ewButtonPanel);

        ewExampleList = new JList();
        ewExampleScrollPane = new JScrollPane(ewExampleList);
        ewListPanel.add(ewExampleScrollPane);
        ewExampleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        String exampleList[] = new String[exampleQueries.examples.size()];
        for (int i = 0; i < exampleQueries.examples.size(); i++) {
            exampleList[i] =
                    ((Query) exampleQueries.
                            examples.get(i)).getDescription();
        }
        ewExampleList.setListData(exampleList);

        ewOkButton = new JButton("Fill in");
        ewOkButton.addActionListener(this);
        ewButtonPanel.add(Box.createHorizontalGlue());
        ewButtonPanel.add(ewOkButton);
        ewButtonPanel.add(Box.createHorizontalGlue());

        exampleWindow.pack();
        exampleWindow.setVisible(true);
    }

    /**
     * Sets up the window used to show the debug output.
     */
    private void createDebugWindow() {
        debugWindow = new JFrame("Debug output");
        debugWindow.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        debugWindow.addWindowListener(this);
        debugWindow.setLocation(500, 100);
        debugWindow.setSize(500, 300);

        dwOutputTextArea = new JTextArea();
        dwOutputScrollPane = new JScrollPane(dwOutputTextArea);
        debugWindow.add(dwOutputScrollPane);

        dwButtonPanel = new JPanel();
        debugWindow.add(dwButtonPanel, BorderLayout.AFTER_LAST_LINE);

        dwClearButton = new JButton("Clear");
        dwClearButton.addActionListener(this);
        dwButtonPanel.add(dwClearButton);
    }

    /**
     * Dispatcher for the various events. Some simple cases may be handled
     * directly within this method.     *
     * @param e
     */
    public final void actionPerformed(final ActionEvent e) {
        if (e.getSource() == mwRunQueryButton) {
            mwQueryButtonPressed();
            return;
        }
        if (e.getSource() == mwServiceInfoButton) {
            mwInfoButtonPressed();
            return;
        }
        if (e.getSource() == mwSubscriptionIdButton) {
                client.querySubscriptionId();
            return;
        }
        if (e.getSource() == mwFillInExampleButton) {
            createExampleWindow();
            return;
        }
        if (e.getSource() == dwClearButton) {
            dwOutputTextArea.setText("");
        }
        if (e.getSource() == mwUnsubscribeQueryButton) {
            client.unsubscribeQuery(mwUnsubscribeQueryTextField.getText());
        }
        if (e.getSource() == ewOkButton) {
            examplesChanged();
            return;
        }
        if (e.getSource() == mwShowDebugWindowCheckBox) {
            debugWindow.setVisible(mwShowDebugWindowCheckBox.isSelected());
            return;
        }
        int i = mwQuerySelectComboBoxes.indexOf(e.getSource());
        if (i >= 0) {
            mwQuerySelectComboBoxesChanged(i);
            return;
        }
    }

    /**
     * Handler for pressed "Run query" button.
     */
    private void mwQueryButtonPressed() {
        dwOutputTextArea.setText("");
        if (resultsWindow != null) {
            resultsWindow.dispose();
        }
        try {
            client.setEndpointAddress(mwServiceUrlTextField.getText());
            client.clearParameters();

            /* get event type selection from GUI */
            Vector<String> eventVector = new Vector<String>();
            if (mwObjectEventsCheckBox.isSelected()) {
                eventVector.add("ObjectEvent");
            }
            if (mwAggregationEventsCheckBox.isSelected()) {
                eventVector.add("AggregationEvent");
            }
            if (mwQuantityEventsCheckBox.isSelected()) {
                eventVector.add("QuantityEvent");
            }
            if (mwTransactionEventsCheckBox.isSelected()) {
                eventVector.add("TransactionEvent");
            }
            String eventArray[] = new String[eventVector.size()];
            eventVector.toArray(eventArray);

            ArrayOfString events = new ArrayOfString();
            events.setString(eventArray);
            client.addParameter(new QueryParam("eventType", events));

            String name;
            for (int i = 0; i < mwQueryArgumentTextFields.size() - 1; i++) {
                name = ((JTextFieldEnhanced) mwQueryArgumentTextFields.get(i))
                                .queryItem.getQueryText();
                switch (((JTextFieldEnhanced)mwQueryArgumentTextFields.get(i))
                                .queryItem.getParamType()) {
                case ListOfString:
                    ArrayOfString valueArray = client.stringListToArray(
                          ((JTextField) mwQueryArgumentTextFields.get(i))
                              .getText());
                    client.addParameter(new QueryParam(name, valueArray));
                    break;
                case Int:
                    Integer valueInteger = Integer.decode(((JTextField)
                            mwQueryArgumentTextFields.get(i)).getText());
                    client.addParameter(new QueryParam(name, valueInteger));
                    break;
                case Time:
                    Calendar valueCalendar = Calendar.getInstance();
                    Date dateTemp;
                    try {
                        dateTemp = isoDateFormat.parse(((JTextField)
                                mwQueryArgumentTextFields.get(i)).getText());
                    } catch (ParseException e) {
                        SimpleDateFormat isoDateFormatTemp =
                            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                        dateTemp = isoDateFormatTemp.parse(((JTextField)
                                mwQueryArgumentTextFields.get(i)).getText());
                    }
                    valueCalendar.setTime(dateTemp);
                    client.addParameter(new QueryParam(name, valueCalendar));
                    break;
                default:
                    String value = ((JTextField) mwQueryArgumentTextFields.
                            get(i)).getText();
                    client.addParameter(new QueryParam(name, value));
                    break;
                }
            }

            if (isSubscribed.isSelected()) {
                if(mwSubIdField.getText().equals("")) {
                    JFrame frame = new JFrame();
                    JOptionPane.showMessageDialog(frame,
                            "Please specify a SubscriptionID",
                            "Service is responding",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                Subscribe subcr = new Subscribe();
                subcr.setDest(new URI(mwDestUriTextField.getText()));
                subcr.setQueryName("SimpleEventQuery");
                subcr.setSubscriptionID(mwSubIdField.getText());
                SubscriptionControls controls = new SubscriptionControls();
                Calendar valueCalendar = Calendar.getInstance();
                Date dateTemp;
                try {
                    dateTemp = isoDateFormat.parse(
                            mwInitRecTimeField.getText());
                } catch (ParseException e) {
                    SimpleDateFormat isoDateFormatTemp =
                        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                    dateTemp = isoDateFormatTemp.parse(
                            mwInitRecTimeField.getText());
                }
                valueCalendar.setTime(dateTemp);
                controls.setInitialRecordTime(valueCalendar);
                controls.setReportIfEmpty(reportIf.isSelected());
                QuerySchedule sched = new QuerySchedule();
                sched.setSecond(mwScheduleSecField.getText());
                sched.setMinute(mwScheduleMinuteField.getText());
                sched.setHour(mwScheduleHourField.getText());
                sched.setDayOfMonth(mwScheduleDayField.getText());
                sched.setMonth(mwScheduleMonthField.getText());
                sched.setDayOfWeek(mwScheduleWeekField.getText());
                controls.setSchedule(sched);
                subcr.setControls(controls);
                client.subscribeQuery(subcr);
                JFrame frame = new JFrame();
                JOptionPane.showMessageDialog(frame, "You have sucessfully "
                        + "subscribed to that Query"
                        , "Service is responding",
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                data = client.runQuery();
                createResultsWindow();
            }
        } catch (AxisFault af) {
            String msg = new String("EPCIS Query Interface service error.\n");
            if (af.getFaultDetails().length >= 2
                    && af.getFaultDetails()[1].getTextContent()
                            .endsWith("ImplementationException")) {
                msg += "Reason: " + af.getFaultDetails()[0].getChildNodes()
                        .item(0).getTextContent() + "\n";
                msg += "Severity: " + af.getFaultDetails()[0].getChildNodes()
                        .item(1).getTextContent();
            } else {
                msg += af.getFaultDetails()[0].getTextContent();
            }
            dwOutputTextArea.append("\nCould not execute query:\n");
            StringWriter detailed = new StringWriter();
            PrintWriter pw = new PrintWriter(detailed);
            af.printStackTrace(pw);
            dwOutputTextArea.append(detailed.toString());

            JFrame frame = new JFrame();
            JOptionPane.showMessageDialog(frame, msg,
                    "EPCIS Query Interface service error",
                    JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            dwOutputTextArea.append("\nCould not execute query:\n");
            StringWriter detailed = new StringWriter();
            PrintWriter pw = new PrintWriter(detailed);
            e.printStackTrace(pw);
            dwOutputTextArea.append(detailed.toString());

            JFrame frame = new JFrame();
            JOptionPane.showMessageDialog(frame,
                    "Could not execute query:\n" + e.getMessage(),
                    "Could not execute query", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Handles the event of a changed JComboBox in the query arguments section
     * Will add or remove JComboBoxes as necessary and resize the window.
     * @param i
     */
    private void mwQuerySelectComboBoxesChanged(final int i) {
        JComboBox cb = (JComboBox) mwQuerySelectComboBoxes.get(i);

        if ((cb.getSelectedIndex() == 0)
                && (cb != mwQuerySelectComboBoxes.getLast())) {
            /* the user selected "ignore" and this is not the last row,
             * so remove it
             */
            removeArgumentRow(i);
        } else if ((cb.getSelectedIndex() != 0)
                && (cb == mwQuerySelectComboBoxes.getLast())) {
            /* the user changed the value of the last row, so add a new row */
            addArgumentRow(i);
        } else {
            /* the user changed an existing row, so just update description  */
            ((JTextFieldEnhanced)
                    mwQueryArgumentTextFields.get(i)).setQueryItem(queryParamsUserText.
                            get(queryParameterUsertext[cb.getSelectedIndex()])
            );
        }
    }

    /**
     * Handles the event of a pressed "info" button. Queries the server for
     * information about it's version and the implemented standard. If this
     * succeedes, one can assume that the connection to the service works
     * fine; if this fails, an error message will be shown to the user with
     * the cause and a stack trace will be printed to the console
     */
    private void mwInfoButtonPressed() {
        dwOutputTextArea.setText("");
        try {
            client.setEndpointAddress(mwServiceUrlTextField.getText());
            String standardVersion = client.queryStandardVersion();
            String vendorVersion = client.queryVendorVersion();
            String[] queryNames = client.queryNames();
            String text = "Service is responding:\n"
                + "Implemented standard: " + standardVersion + "\n"
                + "Service version: " + vendorVersion + "\n"
                + "Supports the following queries: ";
            for (String elem : queryNames) {
                text += elem + "\n";
            }
            JFrame frame = new JFrame();
            JOptionPane.showMessageDialog(frame, text,
                    "Service is responding", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {

            dwOutputTextArea.append("Could not execute query:");
            StringWriter detailed = new StringWriter();
            PrintWriter pw = new PrintWriter(detailed);
            e.printStackTrace(pw);
            dwOutputTextArea.append(detailed.toString());

            JFrame frame = new JFrame();
            JOptionPane.showMessageDialog(frame, "Error:\n" + e.getMessage(),
                    "Service not responding", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Handler for the case that the user chooses an example.
     * Updates the GUI with the example
     */
    private void examplesChanged() {
        int selected = ewExampleList.getSelectedIndex();
        if (selected >= 0) {
            Query ex =
                    (Query) exampleQueries.examples
                            .get(selected);
            mwObjectEventsCheckBox.setSelected(ex.getReturnObjectEvents());
            mwAggregationEventsCheckBox
                    .setSelected(ex.getReturnAggregationEvents());
            mwQuantityEventsCheckBox
                    .setSelected(ex.getReturnQuantityEvents());
            mwTransactionEventsCheckBox
                    .setSelected(ex.getReturnTransactionEvents());

            QueryItem toAddItem = null;
            int i = 0;
            for (QueryParam item : ex.getQueryParametersVector()) {
                toAddItem =  null;
                toAddItem = queryParamsQueryText.get(item.getName());
                if (toAddItem == null) {
                    dwOutputTextArea.append("bugbug: Query example "
                            + "uses unknown queryParam");
                } else {
                    ((JComboBox) mwQuerySelectComboBoxes.get(i))
                            .setSelectedItem(toAddItem.getUserText());
                    ((JTextFieldEnhanced) mwQueryArgumentTextFields.get(i))
                            .setText((String) item.getValue());
                }
                i++;
            }

            /* set the not necessary rows to "ignore" which will delete them */
            int tobedeleted = mwQuerySelectComboBoxes.size() - 1 - i;
            for (int j = 0; j < tobedeleted; j++) {
                ((JComboBox) mwQuerySelectComboBoxes.get(i))
                        .setSelectedIndex(0);
            }

            exampleWindow.setVisible(false);
        }
    }

    /**
     * Removes the row ith row of the query parameters list and
     * updates constraints of the others.
     * Only used by queryselect_changed()
     * @param i
     */
    private void removeArgumentRow(final int i) {
        mwQueryArgsPanel.remove((JComboBox) mwQuerySelectComboBoxes.get(i));
        mwQueryArgsPanel.remove((JTextField) mwQueryArgumentTextFields.get(i));

        mwQuerySelectComboBoxes.remove(i);
        mwQueryArgumentTextFields.remove(i);

        /* Update constraints*/
        GridBagLayout layout = (GridBagLayout) mwQueryArgsPanel.getLayout();
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10,5,5,0);
        for (int j = i; j < mwQuerySelectComboBoxes.size(); j++) {
            c.weightx = 0;
            c.gridx = 0;
            c.gridy = j;
            c.ipadx = 0;
            layout.setConstraints(
                    (JComboBox) mwQuerySelectComboBoxes.get(j), c);
            c.weightx = 1;
            c.gridx = 1;
            c.ipadx = mwQueryArgumentTextFieldsExtraWidth;
            layout.setConstraints(
                    (JTextField) mwQueryArgumentTextFields.get(j), c);
        }
        /* update graphics */
        mainWindow.pack();
        //mainWindow.setSize(mainWindow.getSize().width,
        //        mainWindow.getSize().height - mw_heightdiff);
    }

    /**
     * Adds another row at the end of the query parameters list.
     * Only used by queryselect_changed()
     * @param i
     */
    private void addArgumentRow(final int i) {

        mwQuerySelectComboBoxes.add(new JComboBox(queryParameterUsertext));
        ((JComboBox) mwQuerySelectComboBoxes.getLast())
                .addActionListener(this);
        mwQueryArgumentTextFields.add(new JTextFieldEnhanced(15,
                queryParamsUserText.get("ignore")));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10, 5, 5, 0);
        c.weightx = 0;
        c.gridx = 0;
        c.gridy = i + 1;
        mwQueryArgsPanel.add(
                (JComboBox) mwQuerySelectComboBoxes.getLast(), c);
        c.weightx = 1;
        c.gridx = 1;
        c.ipadx = mwQueryArgumentTextFieldsExtraWidth;
        mwQueryArgsPanel.add(
                (JTextFieldEnhanced) mwQueryArgumentTextFields.getLast(), c);

        /* update tooltip of TextField */
        JComboBox cb = (JComboBox) mwQuerySelectComboBoxes.get(i);
        ((JTextFieldEnhanced)mwQueryArgumentTextFields.get(i)).setQueryItem(
                queryParamsUserText.get(queryParameterUsertext[cb.getSelectedIndex()])
        );

        /* update graphics */
        mainWindow.pack();
        //mainWindow.setSize(mainWindow.getSize().width,
        //        mainWindow.getSize().height + mw_heightdiff);
    }

    /**
     * Event handler for window manager closing events. Overrides the default,
     * empty method.
     */
    public void windowClosing(final WindowEvent e) {
        if (e.getSource() == debugWindow) {
            mwShowDebugWindowCheckBox.setSelected(false);
            return;
        }
    }

    /**
     * A extended JTextField which allows us to store the corresponding
     * QueryItem.
     */
    public class JTextFieldEnhanced extends JTextField {

        /**
		 * 
		 */
		private static final long serialVersionUID = -8874871130001273285L;

		/**
         * The stored QueryItem.
         */
        public QueryItem queryItem;

        /**
         * Constructro which assigns a QueryItem.
         * @param columns for the length of the JTextField
         * @param item which should be stored
         */
        public JTextFieldEnhanced(final int columns, final QueryItem item) {
            super(columns);
            setQueryItem(item);
        }

        /**
         * Default Constructor.
         * @param columns for the length of the JTextField
         */
        public JTextFieldEnhanced(final int columns) {
            super(columns);
        }

        /**
         * Default Constructor.
         */
        public JTextFieldEnhanced() {
            super();
        }

        /**
         * Sets another QueryItem an does the update of the Tooltip.
         * @param item the new QueryItem
         */
        public void setQueryItem(final QueryItem item) {
            this.queryItem = item;
            this.setToolTipText(queryItem.getDescription());
        }
    }

    /**
     * A new class for a QueryItem which can store all its
     * specific features.
     */
    public class QueryItem {

        private Boolean required;

        private String userText;

        private String queryText;

        private String description;

        private ParameterType paramType;

        /**
         * @return the description
         */
        public String getDescription() {
            return description;
        }

        /**
         * @param description the description to set
         */
        public void setDescription(String description) {
            this.description = description;
        }

        /**
         * @return the paramType
         */
        public ParameterType getParamType() {
            return paramType;
        }

        /**
         * @param paramType the paramType to set
         */
        public void setParamType(ParameterType paramType) {
            this.paramType = paramType;
        }

        /**
         * @return the queryText
         */
        public String getQueryText() {
            return queryText;
        }

        /**
         * @param queryText the queryText to set
         */
        public void setQueryText(String queryText) {
            this.queryText = queryText;
        }

        /**
         * @return the required
         */
        public Boolean getRequired() {
            return required;
        }

        /**
         * @param required the required to set
         */
        public void setRequired(Boolean required) {
            this.required = required;
        }

        /**
         * @return the userText
         */
        public String getUserText() {
            return userText;
        }

        /**
         * @param userText the userText to set
         */
        public void setUserText(String userText) {
            this.userText = userText;
        }
    }

    /**
     * Constructor. Tries to set a look-and-feel that matches the current
     * operating system; then creates the GUI.
     *
     * @param args
     */
    public static void main(final String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager
                    .getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.out.println(e.getStackTrace());
        }
        if (args != null && args.length > 0) {
            new QueryClientGui(args[0]);
        } else {
            new QueryClientGui();
        }
    }
}