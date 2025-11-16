package org.example;


import java.sql.*;
import java.util.*;

class BusRouteFinder {
    private Connection conn;

    // Initialize database and create tables
    BusRouteFinder() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:bus_routes.db");
        createTables();
        insertSampleData();
    }

    // Create database tables
    private void createTables() throws SQLException {
        Statement stmt = conn.createStatement();

        stmt.execute("CREATE TABLE IF NOT EXISTS bus (" +
                "id INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "PRIMARY KEY(id AUTOINCREMENT))");

        stmt.execute("CREATE TABLE IF NOT EXISTS bus_route (" +
                "id INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "PRIMARY KEY(id AUTOINCREMENT))");

        stmt.execute("CREATE TABLE IF NOT EXISTS bus_stop (" +
                "id INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "PRIMARY KEY(id AUTOINCREMENT))");

        stmt.execute("CREATE TABLE IF NOT EXISTS route_plan (" +
                "id INTEGER NOT NULL, " +
                "sequence_code NUMERIC NOT NULL, " +
                "bus_id INTEGER NOT NULL, " +
                "bus_stop_id INTEGER NOT NULL, " +
                "bus_route_id INTEGER NOT NULL, " +
                "route_name TEXT, " +
                "FOREIGN KEY(bus_stop_id) REFERENCES bus_stop, " +
                "FOREIGN KEY(bus_id) REFERENCES bus, " +
                "FOREIGN KEY(bus_route_id) REFERENCES bus_route, " +
                "PRIMARY KEY(id AUTOINCREMENT))");

        stmt.close();
    }

    // Insert sample data
    private void insertSampleData() throws SQLException {
        Statement stmt = conn.createStatement();

        // Clear existing data
        stmt.execute("DELETE FROM route_plan");
        stmt.execute("DELETE FROM bus");
        stmt.execute("DELETE FROM bus_route");
        stmt.execute("DELETE FROM bus_stop");

        // Insert buses
        stmt.execute("INSERT INTO bus (name) VALUES ('Bus A'), ('Bus B')");

        // Insert bus routes
        stmt.execute("INSERT INTO bus_route (name) VALUES ('Route 1'), ('Route 2')");

        // Insert bus stops
        stmt.execute("INSERT INTO bus_stop (name) VALUES ('Downtown'), ('Park'), ('Mall'), ('Station'), ('Airport')");

        // Insert route plans
        // Route 1: Downtown -> Park -> Mall
        stmt.execute("INSERT INTO route_plan (sequence_code, bus_id, bus_stop_id, bus_route_id, route_name) " +
                "VALUES (1, 1, 1, 1, 'Route 1'), (2, 1, 2, 1, 'Route 1'), (3, 1, 3, 1, 'Route 1')");

        // Route 2: Park -> Station -> Airport
        stmt.execute("INSERT INTO route_plan (sequence_code, bus_id, bus_id, bus_stop_id, bus_route_id, route_name) " +
                "VALUES (1, 2, 2, 2, 2, 'Route 2'), (2, 2, 2, 4, 2, 'Route 2'), (3, 2, 2, 5, 2, 'Route 2')");

        stmt.close();
    }

    // Find all possible paths between start and end stops
    public List<List<String>> findPaths(String startStop, String endStop) throws SQLException {
        // Get stop IDs
        int startId = getStopId(startStop);
        int endId = getStopId(endStop);

        if (startId == -1 || endId == -1) {
            return new ArrayList<>();
        }

        List<List<String>> allPaths = new ArrayList<>();
        Queue<List<Integer>> queue = new LinkedList<>();
        Queue<List<String>> pathNames = new LinkedList<>();

        // Start with the initial stop
        queue.add(List.of(startId));
        pathNames.add(Arrays.asList(startStop));

        while (!queue.isEmpty()) {
            List<Integer> currentPath = queue.poll();
            List<String> currentPathNames = pathNames.poll();
            int currentStop = currentPath.get(currentPath.size() - 1);

            // If we reached the end stop, add path to results
            if (currentStop == endId) {
                allPaths.add(new ArrayList<>(currentPathNames));
                continue;
            }

            // Get all possible next stops
            PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT DISTINCT rp2.bus_stop_id, bs.name " +
                            "FROM route_plan rp1 " +
                            "JOIN route_plan rp2 ON rp1.bus_route_id = rp2.bus_route_id AND rp1.sequence_code < rp2.sequence_code " +
                            "JOIN bus_stop bs ON rp2.bus_stop_id = bs.id " +
                            "WHERE rp1.bus_stop_id = ?");
            pstmt.setInt(1, currentStop);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                int nextStopId = rs.getInt("bus_stop_id");
                String nextStopName = rs.getString("name");

                // Avoid cycles
                if (!currentPath.contains(nextStopId)) {
                    List<Integer> newPath = new ArrayList<>(currentPath);
                    newPath.add(nextStopId);
                    List<String> newPathNames = new ArrayList<>(currentPathNames);
                    newPathNames.add(nextStopName);

                    queue.add(newPath);
                    pathNames.add(newPathNames);
                }
            }
            rs.close();
            pstmt.close();
        }

        return allPaths;
    }

    // Helper method to get stop ID by name
    private int getStopId(String stopName) throws SQLException {
        PreparedStatement pstmt = conn.prepareStatement(
                "SELECT id FROM bus_stop WHERE name = ?");
        pstmt.setString(1, stopName);
        ResultSet rs = pstmt.executeQuery();

        if (rs.next()) {
            int id = rs.getInt("id");
            rs.close();
            pstmt.close();
            return id;
        }

        rs.close();
        pstmt.close();
        return -1;
    }

    // Close database connection
    public void close() throws SQLException {
        if (conn != null) {
            conn.close();
        }
    }

    // Main method to test the path finder
    public static void main(String[] args) {
        try {
            BusRouteFinder finder = new BusRouteFinder();

            // Find paths from Downtown to Airport
            List<List<String>> paths = finder.findPaths("Downtown", "Airport");

            System.out.println("Possible paths from Downtown to Airport:");
            for (List<String> path : paths) {
                System.out.println(String.join(" -> ", path));
            }

            finder.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}