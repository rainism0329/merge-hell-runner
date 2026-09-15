package com.bigphil.mergehell.model;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small, bounded visibility graph for authored walls. Coordinates describe the whole body's top left. */
final class EnemyTerrainNavigator {
    record Point(double x, double y) { }
    private static final double CLEARANCE = 3;
    private List<Point> path = List.of();
    private int waypoint;

    boolean active() { return waypoint < path.size(); }
    void clear() { path = List.of(); waypoint = 0; }

    boolean plan(double x, double y, int width, int height, Rectangle barrier, int direction,
                 boolean flying, double floor, double left, double right, List<Rectangle> solids) {
        clear();
        double destinationX = direction > 0 ? barrier.getMaxX() + 28 : barrier.x - width - 28;
        double destinationY = flying ? y : floor - height;
        // Adjacent props form one obstacle. Do not choose a landing point inside the next wall.
        for (int attempt = 0; attempt < 8; attempt++) {
            Rectangle overlap = null;
            Rectangle2D landing = new Rectangle2D.Double(destinationX, destinationY, width, height);
            for (Rectangle solid : solids) if (landing.intersects(solid)) { overlap = solid; break; }
            if (overlap == null) break;
            destinationX = direction > 0 ? overlap.getMaxX() + 28 : overlap.x - width - 28;
        }
        if (destinationX < left || destinationX > right) return false;
        Point start = new Point(x, y), end = new Point(destinationX, destinationY);
        if (!clear(end, width, height, solids)) return false;
        double minY = Math.max(24, y - 270), maxY = floor - height;
        double minX = Math.max(left, Math.min(x, destinationX) - 95);
        double maxX = Math.min(right, Math.max(x, destinationX) + 95);
        List<Point> nodes = new ArrayList<>();
        nodes.add(start); nodes.add(end);
        List<Double> wallFaces = new ArrayList<>();
        List<Double> heights = new ArrayList<>(List.of(y, destinationY));
        for (Rectangle solid : solids) {
            if (solid.getMaxX() < minX || solid.x - width > maxX) continue;
            double[] xs = {solid.x - width - CLEARANCE, solid.getMaxX() + CLEARANCE};
            double[] ys = flying ? new double[]{solid.y - height - CLEARANCE, solid.getMaxY() + CLEARANCE}
                    : new double[]{solid.y - height - CLEARANCE};
            if (!flying) {
                for (double px : xs) if (px >= minX && px <= maxX) wallFaces.add(px);
                for (double py : ys) if (py >= minY && py <= maxY) heights.add(py);
                continue;
            }
            for (double px : xs) for (double py : ys) {
                Point point = new Point(px, py);
                if (px >= minX && px <= maxX && py >= minY && py <= maxY
                        && clear(point, width, height, solids)) nodes.add(point);
            }
        }
        if (!flying) for (double px : wallFaces) for (double py : heights) {
            Point point = new Point(px, py);
            if (clear(point, width, height, solids) && !containsPoint(nodes, point)) nodes.add(point);
        }
        int count = nodes.size();
        double[] distance = new double[count];
        int[] previous = new int[count];
        boolean[] visited = new boolean[count];
        java.util.Arrays.fill(distance, Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(previous, -1); distance[0] = 0;
        for (int iteration = 0; iteration < count; iteration++) {
            int current = -1;
            for (int i = 0; i < count; i++)
                if (!visited[i] && (current < 0 || distance[i] < distance[current])) current = i;
            if (current < 0 || !Double.isFinite(distance[current])) return false;
            if (current == 1) break;
            visited[current] = true;
            for (int next = 0; next < count; next++) {
                Point from = nodes.get(current), to = nodes.get(next);
                if (visited[next]) continue;
                if (!flying) {
                    boolean vertical = Math.abs(from.x - to.x) < .001;
                    boolean horizontal = Math.abs(from.y - to.y) < .001;
                    // Feet leave the floor only at a wall face. No diagonal floating from a distant origin.
                    if ((!horizontal && !vertical) || vertical && !wallFaces.contains(from.x)) continue;
                }
                if (!clearPlanningSegment(from, to, width, height, solids)) continue;
                double candidate = distance[current] + length(from, to);
                if (candidate < distance[next]) { distance[next] = candidate; previous[next] = current; }
            }
        }
        if (!Double.isFinite(distance[1]) || distance[1] > 1000) return false;
        List<Point> result = new ArrayList<>();
        for (int i = 1; i != 0 && i >= 0; i = previous[i]) result.add(nodes.get(i));
        Collections.reverse(result);
        path = List.copyOf(result); waypoint = 0;
        return active();
    }

    Point step(double x, double y, double speed, int width, int height,
               double left, double right, List<Rectangle> solids) {
        if (!active()) return new Point(x, y);
        Point from = new Point(x, y), target = path.get(waypoint);
        double remaining = length(from, target);
        double ratio = Math.min(1, speed / Math.max(.001, remaining));
        Point next = new Point(x + (target.x - x) * ratio, y + (target.y - y) * ratio);
        if (next.x < left || next.x > right || !clearSegment(from, next, width, height, solids)) {
            clear(); return from;
        }
        if (remaining <= speed) waypoint++;
        return next;
    }

    static boolean clear(Point point, int width, int height, List<Rectangle> solids) {
        if (solids.isEmpty()) return true;
        Rectangle2D body = new Rectangle2D.Double(point.x + .001, point.y + .001, width - .002, height - .002);
        for (Rectangle solid : solids) if (body.intersects(solid)) return false;
        return true;
    }

    static boolean clearSegment(Point a, Point b, int width, int height, List<Rectangle> solids) {
        if (solids.isEmpty()) return true;
        // Sampling is bounded at two world pixels, finer than the smallest authored obstacle.
        int samples = Math.max(1, (int) Math.ceil(length(a, b) / 2));
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            if (!clear(new Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t), width, height, solids)) return false;
        }
        return true;
    }

    private static boolean clearPlanningSegment(Point from, Point to, int width, int height, List<Rectangle> solids) {
        // Configuration-space rectangles make graph construction cheap even for adjacent walls.
        for (Rectangle solid : solids) {
            var expanded = new Rectangle2D.Double(solid.x - width + .001, solid.y - height + .001,
                    solid.width + width - .002, solid.height + height - .002);
            if (expanded.intersectsLine(from.x, from.y, to.x, to.y)) return false;
        }
        return true;
    }

    private static double length(Point a, Point b) { return Math.hypot(b.x - a.x, b.y - a.y); }
    private static boolean containsPoint(List<Point> points, Point candidate) {
        for (Point point : points) if (point.x == candidate.x && point.y == candidate.y) return true;
        return false;
    }
}
