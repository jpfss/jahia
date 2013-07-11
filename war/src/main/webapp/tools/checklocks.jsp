<%@ page import="org.jahia.ajax.gwt.helper.CacheHelper" %>
<%@ page import="org.jahia.api.Constants" %>
<%@ page import="org.jahia.registries.ServicesRegistry" %>
<%@ page import="org.jahia.services.SpringContextSingleton" %>
<%@ page import="org.jahia.services.content.JCRCallback" %>
<%@ page import="org.jahia.services.content.JCRNodeWrapper" %>
<%@ page import="org.jahia.services.content.JCRSessionWrapper" %>
<%@ page import="org.jahia.services.content.JCRTemplate" %>
<%@ page import="org.jahia.utils.RequestLoadAverage" %>
<%@ page import="org.quartz.JobDetail" %>
<%@ page import="org.quartz.SchedulerException" %>
<%@ page import="javax.jcr.*" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.*" %>
<%@ page import="org.apache.jackrabbit.core.id.NodeId" %>
<%@ page import="org.apache.jackrabbit.core.lock.LockInfo" %>
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="sql" uri="http://java.sun.com/jsp/jstl/sql" %>
<%@taglib prefix="functions" uri="http://www.jahia.org/tags/functions" %>
<c:set var="workspace" value="${functions:default(param.workspace, 'default')}"/>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <link rel="stylesheet" href="tools.css" type="text/css"/>
    <title>JCR Lock Check Tool</title>
    <script type="text/javascript">
        function toggleLayer(whichLayer) {
            var elem, vis;
            if (document.getElementById) // this is the way the standards work
                elem = document.getElementById(whichLayer);
            else if (document.all) // this is the way old msie versions work
                elem = document.all[whichLayer];
            else if (document.layers) // this is the way nn4 works
                elem = document.layers[whichLayer];
            vis = elem.style;
            // if the style.display value is blank we try to figure it out here
            if (vis.display == '' && elem.offsetWidth != undefined && elem.offsetHeight != undefined)
                vis.display = (elem.offsetWidth != 0 && elem.offsetHeight != 0) ? 'block' : 'none';
            vis.display = (vis.display == '' || vis.display == 'block') ? 'none' : 'block';
        }
    </script>
    <style type="text/css">
        div.hiddenDetails {
            margin: 0px 20px 0px 20px;
            display: none;
        }

        .error {
            color: #FF0000;
        }

        .warning {
            color: brown;
        }
    </style>
</head>
<body>
<h1>JCR Lock Check Tool</h1>

<p>
    This tool will perform some integrity checks on the locks of the JCR repository, and also implements some fixes.
</p>

<h2>Lock Integrity checks</h2>
<%!

    private void runJCRTest(final JspWriter out, HttpServletRequest request, final ServletContext servletContext, final boolean fix) throws IOException {

        if (running) {
            println(out, "ABORTING: check or fix already running, please wait for it to complete !");
            return;
        }
        running = true;
        mustStop = false;
        if (fix) {
            if (RequestLoadAverage.getInstance().getOneMinuteLoad() > 1) {
                println(out, "ABORTING: request load is above 1, users are using the platform and we cannot run a fix while this is the case !");
                return;
            }
            try {
                List<JobDetail> activeJobs = ServicesRegistry.getInstance().getSchedulerService().getAllActiveJobs();
                if (activeJobs.size() > 0) {
                    println(out, "ABORTING: background jobs are executing, cannot run fix while background jobs are present !");
                    return;
                }
            } catch (SchedulerException se) {
                errorPrintln(out, "ABORTING: error accessing scheduler service", se, false);
                return;
            }
        }

        long startTime;
        long totalTime;
        if (fix) {
            printTestName(out, "JCR Lock Check with fix activated");
        }
        try {
            String chosenWorkspace = request.getParameter("workspace");
            final Map<String, Long> results = new HashMap<String, Long>();
            results.put("nodesRead", 0L);
            startTime = System.currentTimeMillis();
            for (String workspaceName : workspaces) {
                if (chosenWorkspace == null || chosenWorkspace.isEmpty() || chosenWorkspace.equals(workspaceName)) {
                    JCRTemplate.getInstance().doExecuteWithSystemSession(null, workspaceName, new JCRCallback<Object>() {
                        public Object doInJCR(JCRSessionWrapper sessionWrapper) throws RepositoryException {
                            JCRNodeWrapper jahiaRootNode = sessionWrapper.getRootNode();
                            Node jcrRootNode = jahiaRootNode.getRealNode();
                            Session jcrSession = jcrRootNode.getSession();

                            Workspace workspace = jcrSession.getWorkspace();

                            LinkedHashMap<String, LockData> locks = loadLocks(out, servletContext, workspace.getName());
                            int originalLockFileSize = locks.size();
                            try {
                                println(out, "Traversing " + workspace.getName() + " workspace ...");
                                processNode(out, jcrRootNode, locks, results, fix);
                            } catch (IOException e) {
                                throw new RepositoryException("IOException while running", e);
                            }

                            if (locks.size() > 0) {
                                checkLockFile(out, locks, sessionWrapper, fix);
                            }

                            if (fix && originalLockFileSize != locks.size()) {
                                saveLocks(out, servletContext, locks, workspace.getName());
                            }
                            return null;
                        }
                    });
                }
            }
            if (fix) {
                CacheHelper cacheHelper = (CacheHelper) SpringContextSingleton.getInstance().getContext().getBean("CacheHelper");
                if (cacheHelper != null) {
                    println(out, "Flushing all caches...");
                    cacheHelper.flushAll();
                } else {
                    println(out, "Couldn't find cache helper, please flush all caches manually.");
                }
            }
            long nodesRead = results.get("nodesRead");
            totalTime = System.currentTimeMillis() - startTime;
            println(out, "Total time to process all JCR " + nodesRead + " nodes data : " + totalTime + "ms");
        } catch (Throwable t) {
            errorPrintln(out, "Error reading JCR ", t, false);
        } finally {
            running = false;
            mustStop = false;
        }

    }

    private void printTestName(JspWriter out, String testName) throws IOException {
        out.println("<h3>");
        println(out, testName);
        out.println("</h3>");
    }

    private static SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd-HHmmss");

    private String generatePadding(int depth, boolean withNbsp) {
        StringBuffer padding = new StringBuffer();
        for (int i = 0; i < depth; i++) {
            if (withNbsp) {
                padding.append("&nbsp;&nbsp;");
            } else {
                padding.append("  ");
            }
        }
        return padding.toString();
    }

    int errorCount = 0;

    private void print(JspWriter out, String message) {
        System.out.print(message);
        try {
            out.print(message);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void println(JspWriter out, String message) {
        System.out.println(message);
        try {
            out.println(message + "<br/>");
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void depthPrintln(JspWriter out, int depth, String message) {
        System.out.println(generatePadding(depth, false) + message);
        try {
            out.println(generatePadding(depth, true) + message + "<br/>");
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void debugPrintln(JspWriter out, String message) {
        System.out.println("DEBUG: " + message);
        try {
            out.println("<!--" + message + "-->");
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void errorPrintln(JspWriter out, String message) {
        System.out.println("ERROR: " + message);
        try {
            out.println("<span class='error'>" + message + "</span><br/>");
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void depthErrorPrintln(JspWriter out, int depth, String message) {
        System.out.println(generatePadding(depth, false) + "ERROR: " + message);
        try {
            out.println(generatePadding(depth, true) + "<span class='error'>" + message + "</span><br/>");
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void errorPrintln(JspWriter out, String message, Throwable t, boolean warning) {
        System.out.println(message);
        if (t != null) {
            t.printStackTrace();
        }
        try {
            if (warning) {
                out.println("<span class='warning'>" + message + "</span>");
            } else {
                out.println("<span class='error'>" + message + "</span>");
            }
            errorCount++;
            if (t != null) {
                out.println("<a href=\"javascript:toggleLayer('error" + errorCount + "');\" title=\"Click here to view error details\">Show/hide details</a>");
                out.println("<div id='error" + errorCount + "' class='hiddenDetails'><pre>");
                t.printStackTrace(new PrintWriter(out));
                out.println("</pre></div>");
            }
            out.println("<br/>");
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void processNode(JspWriter out, Node node, LinkedHashMap<String, LockData> locks, Map<String, Long> results, boolean fix) throws IOException, RepositoryException {
        long nodesRead = results.get("nodesRead");
        // first let's try to read all the properties
        try {
            // node let's recurse into subnodes.
            nodesRead++;
            if (nodesRead % 1000 == 0) {
                println(out, "Processed " + nodesRead + " nodes...");
            }
            results.put("nodesRead", nodesRead);
            if (node.isNodeType("mix:lockable")) {
                if (node.hasProperty("jcr:lockIsDeep")) {
                    if (node.hasProperty("jcr:lockOwner")) {
                        if (node.isNodeType("jmix:lockable")) {
                            if (node.hasProperty("j:locktoken")) {
                                String lockToken = node.getProperty("j:locktoken").getString();
                                if (locks.containsKey(node.getIdentifier())) {
                                    LockData lockData = locks.get(node.getIdentifier());
                                    if (lockToken.equals(lockData.getToken())) {
                                        // all is good, nothing to do here.
                                        StringBuffer lockTypes = new StringBuffer();
                                        if (node.hasProperty("j:lockTypes")) {
                                            Property lockTypeProperty = node.getProperty("j:lockTypes");
                                            Value[] lockTypeValues = lockTypeProperty.getValues();
                                            for (Value lockTypeValue : lockTypeValues) {
                                                lockTypes.append(lockTypeValue.getString());
                                                lockTypes.append(" ");
                                            }
                                        }
                                        println(out, "Normal lock found at " + node.getPath() +
                                                " token=" + lockToken +
                                                " lockTypes=" + lockTypes.toString() +
                                                " lockOwner=" + node.getProperty("jcr:lockOwner").getString() +
                                                " lockIsDeep=" + node.getProperty("jcr:lockIsDeep").getBoolean()
                                        );
                                    } else {
                                        errorPrintln(out, "Property j:locktoken (=" + lockToken + ") on node " + node.getPath() + " and lock file token (=" + lockData.getToken() + ") do not match !");
                                        if (fix) {
                                            errorPrintln(out, "Setting property j:locktoken on node " + node.getPath() + " to value of lock file =" + lockData.getToken());
                                            boolean checkedOut = false;
                                            if (!node.isCheckedOut()) {
                                                node.checkout();
                                                checkedOut = true;
                                            }
                                            node.setProperty("j:locktoken", lockData.getToken());
                                            if (checkedOut) {
                                                node.checkin();
                                            }
                                            node.getSession().save();
                                        }
                                    }
                                } else {
                                    // missing the lock token in the locks file
                                    errorPrintln(out, "Lock token is missing in the lock file !");
                                    if (fix) {
                                        errorPrintln(out, "Generating new lock token for lock file...");
                                        String lockIdentifier = node.getIdentifier();
                                        String newLockToken = getLockToken(lockIdentifier);
                                        String lockLine = newLockToken;
                                        LockData lockData = new LockData(node.getIdentifier(), newLockToken, lockLine, Long.MAX_VALUE);
                                        locks.put(lockIdentifier, lockData);
                                    }
                                }
                            } else {
                                errorPrintln(out, "Missing j:locktoken on node " + node.getPath());
                                if (fix) {
                                    // let's try to find the token in the lock map to see if we can rebuild it.
                                    if (locks.containsKey(node.getIdentifier())) {
                                        errorPrintln(out, "Rebuilding j:locktoken property from lock file");
                                        LockData lockData = locks.get(node.getIdentifier());
                                        boolean checkedOut = false;
                                        if (!node.isCheckedOut()) {
                                            node.checkout();
                                            checkedOut = true;
                                        }
                                        node.setProperty("j:locktoken", lockData.getToken());
                                        if (checkedOut) {
                                            node.checkin();
                                        }
                                        node.getSession().save();
                                    } else {
                                        // no token was found, we will clear the lock from both the properties
                                        // and the locks
                                        errorPrintln(out, "Couldn't find lock token in lock file, will regenerate it and");
                                        String newLockToken = getLockToken(node.getIdentifier());
                                        String lockLine = newLockToken;
                                        LockData lockData = new LockData(node.getIdentifier(), newLockToken, lockLine, Long.MAX_VALUE);
                                        locks.put(node.getIdentifier(), lockData);
                                        boolean checkedOut = false;
                                        if (!node.isCheckedOut()) {
                                            node.checkout();
                                            checkedOut = true;
                                        }
                                        node.setProperty("j:locktoken", newLockToken);
                                        if (checkedOut) {
                                            node.checkin();
                                        }
                                        node.getSession().save();
                                    }
                                }
                            }
                            if (node.isNodeType("jnt:translation")) {
                                // we are in a locked translation node, we need to check the status of the parent.
                                Node parentNode = node.getParent();
                                // test if parent is locked
                                if (parentNode.isNodeType("mix:lockable") &&
                                        !parentNode.hasProperty("jcr:lockIsDeep") &&
                                        !parentNode.hasProperty("jcr:lockOwner") &&
                                        parentNode.isNodeType("jmix:lockable") &&
                                        !parentNode.hasProperty("j:locktoken")) {
                                    // parent doesn't appear to be locked, we must remove the lock on the translation node
                                    errorPrintln(out, "Parent node of translation node " + node.getPath() + " is not locked but translation node is locked !");
                                    if (fix) {
                                        errorPrintln(out, "Unlocking translation node");
                                        boolean checkedOut = false;
                                        if (!node.isCheckedOut()) {
                                            node.checkout();
                                            checkedOut = true;
                                        }
                                        if (node.hasProperty("jcr:lockIsDeep")) {
                                            node.getProperty("jcr:lockIsDeep").remove();
                                        }
                                        if (node.hasProperty("jcr:lockOwner")) {
                                            node.getProperty("jcr:lockOwner").remove();
                                        }
                                        if (checkedOut) {
                                            node.checkin();
                                        }
                                        node.getSession().save();
                                        if (locks.containsKey(node.getIdentifier())) {
                                            locks.remove(node.getIdentifier());
                                        }
                                    }
                                }
                            }
                        } else {
                            // now we must check if the lock is present in the lock file
                            if (locks.containsKey(node.getIdentifier())) {
                                // this is ok
                            } else {
                                errorPrintln(out, "Entry missing in lock file for node " + node.getPath());
                                if (fix) {
                                    errorPrintln(out, "Generating new lock token for node " + node.getPath() + " and storing in locks file");
                                    String newLockToken = getLockToken(node.getIdentifier());
                                    String lockLine = newLockToken;
                                    LockData lockData = new LockData(node.getIdentifier(), newLockToken, lockLine, Long.MAX_VALUE);
                                    locks.put(node.getIdentifier(), lockData);
                                }
                            }
                        }
                    } else {
                        errorPrintln(out, "Found jcr:lockIsDeep property on node " + node.getPath() + " but no matching jcr:lockOwner is present !");
                        if (fix) {
                            errorPrintln(out, "NOT YET IMPLEMENTED !");
                        }
                    }
                } else {
                    if (node.isNodeType("jmix:lockable") && node.hasProperty("j:locktoken")) {
                        // the node doesn't have a jcr:lockIsDeep but a j:locktoken, so we need to decide
                        // if the node should be locked or not.
                        errorPrintln(out, "Found node " + node.getPath() + " with a j:locktoken property but no jcr:lockIsDeep is present");
                        if (fix) {
                            errorPrintln(out, "Clean(removing) lock properties on node " + node.getPath());
                            boolean checkedOut = false;
                            if (!node.isCheckedOut()) {
                                node.checkout();
                                checkedOut = true;
                            }
                            if (node.hasProperty("j:locktoken")) {
                                node.getProperty("j:locktoken").remove();
                            }
                            if (node.hasProperty("jcr:lockOwner")) {
                                node.getProperty("jcr:lockOwner").remove();
                            }
                            if (node.hasProperty("j:lockTypes")) {
                                node.getProperty("j:lockTypes").remove();
                            }
                            if (checkedOut) {
                                node.checkin();
                            }
                            node.getSession().save();
                            if (locks.containsKey(node.getIdentifier())) {
                                locks.remove(node.getIdentifier());
                            }
                        }
                    }
                }
            }
            NodeIterator childNodeIterator = node.getNodes();
            while (childNodeIterator.hasNext() && !mustStop) {
                Node childNode = childNodeIterator.nextNode();
                if (childNode.getName().equals("jcr:system")) {
                    println(out, "Ignoring jcr:system node and it's child objects");
                } else {
                    processNode(out, childNode, locks, results, fix);
                }
            }
        } catch (ValueFormatException vfe) {
            errorPrintln(out, "ValueFormatException while processing node" + node.getPath(), vfe, false);
        } catch (RepositoryException re) {
            errorPrintln(out, "RepositoryException while processing node" + node.getPath(), re, false);
        }
    }

    protected void checkLockFile(JspWriter out, LinkedHashMap<String, LockData> locks, Session session, boolean fix) {
        println(out, "Checking lock file data...");
        for (Map.Entry<String, LockData> lockDataEntry : locks.entrySet()) {
            LockData lockData = lockDataEntry.getValue();
            try {
                Node node = session.getNodeByIdentifier(lockData.getUuid());
                if (node == null) {

                } else if (node.hasProperty("jcr:lockIsDeep") && node.hasProperty("jcr:lockOwner") && node.hasProperty("j:locktoken")) {
                    // everything checks out, nothing to do
                } else {
                    // node is missing one of the lock properties, we need to cleanup the best we can.
                    if (node.hasProperty("j:locktoken")) {
                        // this is more serious, we have a lock token but no JCR lock properties on the node itself
                        errorPrintln(out, "Found property j:locktoken on node " + node.getPath() + " but couldn't find property jcr:lockIsDeep or jcr:lockOwner !");
                    } else {
                        if (node.hasProperty("jcr:lockIsDeep") && node.hasProperty("jcr:lockOwner")) {
                            // we have the mandatory system properties, we can simply reconstruct the j:locktoken property
                            errorPrintln(out, "Missing property j:locktoken on node " + node.getPath() + " ");
                            if (fix) {
                                errorPrintln(out, "Rebuilding property j:locktoken on node " + node.getPath() + " ");
                                boolean checkedOut = false;
                                if (!node.isCheckedOut()) {
                                    node.checkout();
                                    checkedOut = true;
                                }
                                node.setProperty("j:locktoken", lockData.getToken());
                                if (checkedOut) {
                                    node.checkin();
                                }
                                node.getSession().save();
                            }
                        } else {
                            // we are missing one of the mandatory JCR system properties and the lock token, we should
                            // probably try to clean out the lock
                            errorPrintln(out, "Missing property j:locktoken on node " + node.getPath() + " and also jcr:lockIsDeep and/or jcr:lockOwner ");
                            if (fix) {

                            }
                        }
                    }
                }
            } catch (RepositoryException e) {
                e.printStackTrace();
            }
        }
    }

    private void renderRadio(JspWriter out, String radioValue, String radioLabel, boolean checked) throws IOException {
        out.println("<input type=\"radio\" name=\"operation\" value=\"" + radioValue
                + "\" id=\"" + radioValue + "\""
                + (checked ? " checked=\"checked\" " : "")
                + "/><label for=\"" + radioValue + "\">"
                + radioLabel
                + "</label><br/>");
    }

    private void renderCheckbox(JspWriter out, String checkboxValue, String checkboxLabel, boolean checked) throws IOException {
        out.println("<input type=\"checkbox\" name=\"option\" value=\"" + checkboxValue
                + "\" id=\"" + checkboxValue + "\""
                + (checked ? " checked=\"checked\" " : "")
                + "/><label for=\"" + checkboxValue + "\">"
                + checkboxLabel
                + "</label><br/>");
    }

    private void renderWorkspaceSelector(JspWriter out) throws IOException {
        out.println("<label for=\"workspaceSelector\">Choose workspace:</label>" +
                "<select id=\"workspaceSelector\" name=\"workspace\"><option value=\"\">All Workspaces</option>");
        for (String workspace : workspaces) {
            out.println("<option value=\"" + workspace + "\">" + workspace + "</option>");
        }
        out.println("</select><br/>");
    }

    private boolean isParameterActive(HttpServletRequest request, String parameterName, String operationName) {
        String[] operationValues = request.getParameterValues(parameterName);
        if (operationValues == null) {
            return false;
        }
        for (String operationValue : operationValues) {
            if (operationValue.equals(operationName)) {
                return true;
            }
        }
        return false;
    }

    private LinkedHashMap<String, LockData> loadLocks(JspWriter out, ServletContext servletContext, String workspaceName) {
        LinkedHashMap<String, LockData> locks = new LinkedHashMap<String, LockData>();

        BufferedReader reader = null;
        try {
            String lockFilePath = "/WEB-INF/var/repository/workspaces/" + workspaceName + "/locks";

            InputStream locksInputStream = servletContext.getResourceAsStream(lockFilePath);
            if (locksInputStream == null) {
                println(out, "No lock file found at " + lockFilePath);
                return locks;
            }

            reader = new BufferedReader(
                    new InputStreamReader(locksInputStream));
            while (true) {
                String s = reader.readLine();
                if (s == null || s.equals("")) {
                    break;
                }
                String[] parts = s.split(",");
                String token = parts[0];
                long timeoutHint = Long.MAX_VALUE;
                if (parts.length > 1) {
                    try {
                        timeoutHint = Long.parseLong(parts[1]);
                    } catch (NumberFormatException e) {
                        errorPrintln(out, "Unexpected timeout hint "
                                + parts[1] + " for lock token " + token + ":" + e);
                    }
                }
                NodeId id = LockInfo.parseLockToken(token);
                LockData lockData = new LockData(id.toString(), token, s, timeoutHint);
                locks.put(id.toString(), lockData);
            }
            println(out, "Loaded " + locks.size() + " locks from lock file " + lockFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return locks;
    }

    private boolean saveLocks(JspWriter out, ServletContext servletContext, LinkedHashMap<String, LockData> locks, String workspaceName) {

        String lockFileDiskPath = servletContext.getRealPath("/WEB-INF/var/repository/workspaces/" + workspaceName + "/locks.corrected");
        if (lockFileDiskPath == null) {
            return false;
        }
        File lockFile = new File(lockFileDiskPath);

        BufferedWriter writer = null;

        try {
            writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(lockFile)));
            for (Map.Entry<String, LockData> info : locks.entrySet()) {
                writer.write(info.getValue().getToken());

                // Store the timeout hint, if one is specified
                if (info.getValue().getTimeoutHint() != Long.MAX_VALUE) {
                    writer.write(',');
                    writer.write(Long.toString(info.getValue().getTimeoutHint()));
                }

                writer.newLine();
            }
            println(out, "Successfully wrote " + locks.size() + " locks to file " + lockFile);
            println(out, "Please shutdown Jahia, rename existing locks file to locks.backup and rename " + lockFile + " to locks");
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    /**
     * Return the check digit for a lock token, given by its UUID
     * @param uuid uuid
     * @return check digit
     */
    private static char getLockTokenCheckDigit(String uuid) {
        int result = 0;

        int multiplier = 36;
        for (int i = 0; i < uuid.length(); i++) {
            char c = uuid.charAt(i);
            if (c >= '0' && c <= '9') {
                int num = c - '0';
                result += multiplier * num;
                multiplier--;
            } else if (c >= 'A' && c <= 'F') {
                int num = c - 'A' + 10;
                result += multiplier * num;
                multiplier--;
            } else if (c >= 'a' && c <= 'f') {
                int num = c - 'a' + 10;
                result += multiplier * num;
                multiplier--;
            }
        }

        int rem = result % 37;
        if (rem != 0) {
            rem = 37 - rem;
        }
        if (rem >= 0 && rem <= 9) {
            return (char) ('0' + rem);
        } else if (rem >= 10 && rem <= 35) {
            return (char) ('A' + rem - 10);
        } else {
            return '+';
        }
    }

    private String getLockToken(String uuid) {
        return uuid + "-" + getLockTokenCheckDigit(uuid);
    }

    // by default in the case of an invalid reference we will simply reset it to null, but if the node type is listed
    // in the following list, we will remove the parent node completely. This can be the case for group members, where
    // if the reference doesn't exist we want to remove the parent node completely. It is recommended that this list
    // be minimal as it does delete the node !
    static String[] invalidReferenceNodeTypesToRemove = new String[]{
            "nt:linkedFile",
            "jnt:member",
            "jnt:reference"
    };

    static boolean running = false;
    static boolean mustStop = false;
    static String[] workspaces = new String[]{"default", "live"};

    public class LockData {
        private String uuid;
        private String token;
        private String lockLine;
        private long timeoutHint;

        public LockData(String uuid, String token, String lockLine, long timeoutHint) {
            this.uuid = uuid;
            this.token = token;
            this.lockLine = lockLine;
            this.timeoutHint = timeoutHint;
        }

        public String getUuid() {
            return uuid;
        }

        public String getToken() {
            return token;
        }

        public String getLockLine() {
            return lockLine;
        }

        public long getTimeoutHint() {
            return timeoutHint;
        }
    }
%>
<%
    if (request.getParameterMap().size() > 0) {

        if (isParameterActive(request, "operation", "runJCRTest")) {
            runJCRTest(out, request, application, false);
        }

        if (isParameterActive(request, "operation", "fixJCR")) {
            runJCRTest(out, request, application, true);
        }

        if (isParameterActive(request, "operation", "stop")) {
            mustStop = true;
        }

        out.println("<h2>Test completed.</h2>");
    } else {
        if (!running) {
            out.println("<form>");
            renderWorkspaceSelector(out);
            renderRadio(out, "runJCRTest", "Run Java Content Repository integrity check", true);
            renderRadio(out, "fixJCR", "Fix full Java Content Repository integrity (also performs check). DO NOT RUN IF PLATFORM IS ACTIVE (USERS, BACKGROUND JOBS ARE RUNNING !). Also this operation WILL DELETE node with invalid references so please backup your data before running this fix ! Also you MUST RESTART JAHIA after running this fix !", false);
            out.println("<input type=\"submit\" name=\"submit\" value=\"Submit\">");
            out.println("</form>");
        } else {
            out.println("<form>");
            renderCheckbox(out, "stop", "Stop currently running check/fix", true);
            out.println("<input type=\"submit\" name=\"submit\" value=\"Submit\">");
            out.println("</form>");
        }
    }

%>
<%@ include file="gotoIndex.jspf" %>
</body>
</html>