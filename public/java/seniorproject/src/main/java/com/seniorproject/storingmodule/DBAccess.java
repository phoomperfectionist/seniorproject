/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.seniorproject.storingmodule;

import au.com.bytecode.opencsv.CSVWriter;
import com.seniorproject.graphmodule.Edge;
import com.seniorproject.graphmodule.EdgeIterable;
import com.seniorproject.graphmodule.Node;
import com.seniorproject.graphmodule.NodeIterable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

public class DBAccess {

    private String DATABASE_DIR;
    private String MIGRATE_DIR;
    private String SOURCE_DATABASE;

    public DBAccess() {
        readConfigFile();
    }

    private void readConfigFile() {
        Properties prop = new Properties();
        InputStream input = null;

        try {
            File jarPath=new File(DBAccess.class.getProtectionDomain().getCodeSource().getLocation().getPath());
            String propertiesPath=jarPath.getParentFile().getAbsolutePath();
            prop.load(new FileInputStream(propertiesPath+"/config.properties"));

            DATABASE_DIR = prop.getProperty("database_dir");
            SOURCE_DATABASE = prop.getProperty("source_database");
            MIGRATE_DIR = prop.getProperty("migrate_dir");
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Convert list of string into regular expression
     *
     * @param s - List of String
     * @return regular expression created from list of string
     *
     */
    public static String toRegex(List<String> s) {
        String regex = s.get(0);
        for (int i = 1; i < s.size(); i++) {
            regex += regex + "|" + s.get(i);
        }
        return regex;
    }

    /**
     * Query nodes and relationships from embedded Neo4J database
     *
     * @param sFilters - Map containing string-type filter name and value
     * @param fFilters - Map containing numeric-type filter name and value
     * @param db - database name
     *
     * @return graph object containing filtered nodes and edges
     *
     */
    public com.seniorproject.graphmodule.Graph loadAll(Map<String, List<String>> sFilters,
            Map<String, List<Double>> fFilters, String db) {

        Set<Node> nodes = new HashSet<>();
        List<Edge> edges = new ArrayList<>();

        String rnCode_Regex = "";
        String callDay_Regex = "";

        for (String rnCode : sFilters.get("rnCode")) {
            rnCode_Regex = rnCode_Regex + rnCode + "|";
        }
        for (String callDay : sFilters.get("callDay")) {
            callDay_Regex = callDay_Regex + callDay + "|";
        }
        rnCode_Regex = rnCode_Regex.substring(0, rnCode_Regex.length() - 1);
        callDay_Regex = callDay_Regex.substring(0, callDay_Regex.length() - 1);
        GraphDatabaseService graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(DATABASE_DIR + SOURCE_DATABASE);

        try {

            Map<String, Object> params = new HashMap<>();
            params.put("rnCode", rnCode_Regex);
            params.put("callDay", callDay_Regex);
            params.put("durationMin", fFilters.get("duration").get(0));
            params.put("durationMax", fFilters.get("duration").get(1));
            params.put("startTime", fFilters.get("startTime").get(0));
            params.put("endTime", fFilters.get("startTime").get(1));
            params.put("startDate", fFilters.get("startDate").get(0));
            params.put("endDate", fFilters.get("startDate").get(1));
            params.put("incomingMin", fFilters.get("incoming").get(0));
            params.put("incomingMax", fFilters.get("incoming").get(1));
            params.put("outgoingMin", fFilters.get("outgoing").get(0));
            params.put("outgoingMax", fFilters.get("outgoing").get(1));

            String cypher = "MATCH (n:" + db + ")-[r:Call]->(m) ";
            cypher = cypher + "WHERE "; //n.carrier =~ {rnCode} AND m.carrier =~ {rnCode} AND ";
            cypher = cypher + "r.duration >= {durationMin} AND r.duration <= {durationMax} AND r.callDay =~ {callDay}  AND "
                    + "r.startTime >= {startTime} AND r.startTime <= {endTime} AND r.startDate >= {startDate} AND r.startDate <= {endDate} ";
            cypher = cypher + "RETURN ID(n) as nnid,n.number, n.age, n.gender, n.incoming, n.outgoing, n.promotion, n.carrier, n.arpu, "
                    + "ID(m) as mmid, m.number, m.age, m.gender, m.incoming, m.outgoing, m.promotion, m.carrier, m.arpu,"
                    + "r.duration, r.startDate, r.startTime, r.callDay";

            try (Transaction tx = graphDb.beginTx();
                    Result result = graphDb.execute(cypher, params)) {
                tx.success();
                Map<Integer, Integer> incoming = new HashMap<>();
                Map<Integer, Integer> outgoing = new HashMap<>();
                while (result.hasNext()) {
                    Map<String, Object> row = result.next();

                    // Hashmap for storing attributes
                    Map<String, Object> a = new HashMap<>();
                    Map<String, Object> b = new HashMap<>();
                    Map<String, Object> r = new HashMap<>();

                    // Get all attribues returned from executing cypher
                    // Note : Returning order is uncontrollable;
                    for (Entry<String, Object> column : row.entrySet()) {

                        String domain = column.getKey().substring(0, 1);
                        String attr = column.getKey().substring(2);

                        switch (domain) {
                            case "n":
                                a.put(attr, column.getValue());
                                break;
                            case "m":
                                b.put(attr, column.getValue());
                                break;
                            case "r":
                                r.put(attr, column.getValue());
                                break;
                            default:
                                // This should not be triggered
                                throw new Exception("Invalid Cypher Return : Load All Function");
                        }
                    }

                    int aid = Integer.parseInt(a.get("id").toString()),
                            bid = Integer.parseInt(b.get("id").toString());

                    Node caller = new Node(aid);
                    Node callee = new Node(bid);
                    caller.setLabel(a.get("number").toString());
                    callee.setLabel(b.get("number").toString());
                    
                    if(incoming.containsKey(bid)) {
                        incoming.put(bid, incoming.get(bid) + 1);
                    } else {
                        incoming.put(bid, 1);
                    }
                    
                    if(outgoing.containsKey(aid)) {
                        outgoing.put(aid, outgoing.get(aid) + 1);
                    } else {
                        outgoing.put(aid, 1);
                    }
                    
                    a.remove("id");
                    b.remove("id");
                    a.remove("number");
                    b.remove("number");

                    caller.setProperties(a);
                    callee.setProperties(b);

                    Edge rel = new Edge(
                            aid,
                            bid,
                            Integer.parseInt(r.get("duration").toString()),
                            Long.toString(Double.valueOf(r.get("startDate").toString()).longValue()),
                            r.get("startTime").toString(),
                            r.get("callDay").toString(),
                            Integer.parseInt(r.get("duration").toString()),
                            callee.getProperty("arpu").toString().equals("unknown") ? "unknown" : "AIS"
                    );

                    nodes.add(caller);
                    nodes.add(callee);
                    edges.add(rel);
                }
                
                for(Node tmp : nodes) {
                    
                    if(incoming.containsKey(tmp.getID())) {
                        tmp.setProperty("incoming", incoming.get(tmp.getID()));
                    } else {
                        tmp.setProperty("incoming", 0);
                    }
                    
                    if(outgoing.containsKey(tmp.getID())) {
                        tmp.setProperty("outgoing", outgoing.get(tmp.getID()));
                    } else {
                        tmp.setProperty("outgoing", 0);
                    }
                    
                    nodes.add(tmp);
                }
                
                return new com.seniorproject.graphmodule.Graph(nodes, edges);
            } catch (Exception e) {
                System.out.println("======== Error occured in LoadAll function ========");
                System.err.println(e.getMessage());
                System.out.println("======================================");
            }
        } finally {
            System.out.println("Graph is shut down successfully");
            graphDb.shutdown();
        }
        return null;
    }

    /**
     * Store nodes and edges as customers in CSV format
     *
     * @param nodes - NodeIterable containing all nodes to be stored
     * @param aggregatedEdges - List of aggregated edges to be stored (no more than one edge between each pair of nodes)
     * @param edges - List of edges to be stored
     * @param tid - Table ID
     * @throws java.io.IOException
     *
     */
    public void store(NodeIterable nodes, EdgeIterable aggregatedEdges, List<Edge> edges, String tid) throws IOException {
        Map<Integer, String> numberMapper = new HashMap<>();
        CSVWriter writer = new CSVWriter(new FileWriter(MIGRATE_DIR + "processed_" + tid + "_profile.csv"), ',');

        boolean isFirst = true;
        String[] line = null;

        for (Node n : nodes) {
            if (isFirst) {
                line = n.getPropertiesName();
                writer.writeNext(line);
                isFirst = false;
            }
            line = n.splitPropertiesWithLabel();
            numberMapper.put(n.getID(), n.getLabel());
            writer.writeNext(line);
        }
        writer.close();

        writer = new CSVWriter(new FileWriter(MIGRATE_DIR + "processed_" + tid + "_full_cdr.csv"), ',');
        isFirst = true;
        for (Edge e : edges) {
            if (isFirst) {
                line = e.getPropertiesName();
                writer.writeNext(line);
                isFirst = false;
            }
            line = e.splitPropertiesWithNode();

            line[0] = numberMapper.get(Integer.parseInt(line[0]));
            line[1] = numberMapper.get(Integer.parseInt(line[1]));

            writer.writeNext(line);
        }
        writer.close();
        
        writer = new CSVWriter(new FileWriter(MIGRATE_DIR + "processed_" + tid + "_aggregated_cdr.csv"), ',');
        isFirst = true;
        for (Edge e : aggregatedEdges) {
            if (isFirst) {
                line = e.getPropertiesName();
                writer.writeNext(line);
                isFirst = false;
            }
            line = e.splitPropertiesWithNode();

            line[0] = numberMapper.get(Integer.parseInt(line[0]));
            line[1] = numberMapper.get(Integer.parseInt(line[1]));

            writer.writeNext(line);
        }
        writer.close();
    }

    /**
     * Store nodes and edges as communities in CSV format
     *
     * @param nodes - NodeIterable containing all nodes to be stored
     * @param aggregatedEdges
     * @param edges - List of edges to be stored
     * @param tid - Table ID
     * @throws java.io.IOException
     *
     */
    public void storeCommunity(NodeIterable nodes, EdgeIterable aggregatedEdges, List<Edge> edges, String tid) throws IOException {
        Map<Integer, String> numberMapper = new HashMap<>();
        CSVWriter writer = new CSVWriter(new FileWriter(MIGRATE_DIR + "processed_com_" + tid + "_profile.csv"), ',');

        boolean isFirst = true;
        String[] line = null;

        for (Node n : nodes) {
            if (isFirst) {
                line = n.getPropertiesName();
                writer.writeNext(line);
                isFirst = false;
            }
            line = n.splitPropertiesWithLabel();
            line[0] = n.getProperty("communityID").toString();
            numberMapper.put(n.getID(), line[0]);
            writer.writeNext(line);
        }
        writer.close();

        writer = new CSVWriter(new FileWriter(MIGRATE_DIR + "processed_com_" + tid + "_full_cdr.csv"), ',');
        isFirst = true;
        for (Edge e : edges) {
            if (isFirst) {
                line = e.getPropertiesName();
                writer.writeNext(line);
                isFirst = false;
            }
            line = e.splitPropertiesWithNode();

            line[0] = numberMapper.get(Integer.parseInt(line[0]));
            line[1] = numberMapper.get(Integer.parseInt(line[1]));

            writer.writeNext(line);
        }
        writer.close();
        
        writer = new CSVWriter(new FileWriter(MIGRATE_DIR + "processed_com_" + tid + "_aggregated_cdr.csv"), ',');
        isFirst = true;
        for (Edge e : aggregatedEdges) {
            if (isFirst) {
                line = e.getPropertiesName();
                writer.writeNext(line);
                isFirst = false;
            }
            line = e.splitPropertiesWithNode();

            line[0] = numberMapper.get(Integer.parseInt(line[0]));
            line[1] = numberMapper.get(Integer.parseInt(line[1]));

            writer.writeNext(line);
        }
        writer.close();
    }
}
