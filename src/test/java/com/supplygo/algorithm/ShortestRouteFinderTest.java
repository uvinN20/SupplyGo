package com.supplygo.algorithm;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.supplygo.model.RouteGraph;

class ShortestRouteFinderTest {
    RouteGraph graph(List<RouteGraph.Road> roads) { return new RouteGraph(1, List.of(new RouteGraph.Location("A", "A"), new RouteGraph.Location("B", "B"), new RouteGraph.Location("C", "C"), new RouteGraph.Location("D", "D")), roads); }
    @Test void shorterIndirectPathAndReverseDirectionBeatDirectRoad() {
        var graph = graph(List.of(RouteGraph.Road.between("A", "C", 50), RouteGraph.Road.between("A", "B", 10), RouteGraph.Road.between("B", "C", 15)));
        assertEquals(new ShortestRouteFinder.Result(List.of("A", "B", "C"), 25), ShortestRouteFinder.find(graph, "A", "C").orElseThrow());
        assertEquals(List.of("C", "B", "A"), ShortestRouteFinder.find(graph, "C", "A").orElseThrow().path());
    }
    @Test void sameLocationDisconnectedAndUnknownHaveDistinctResults() {
        var graph = graph(List.of()); assertEquals(0, ShortestRouteFinder.find(graph, "A", "A").orElseThrow().meters());
        assertTrue(ShortestRouteFinder.find(graph, "A", "D").isEmpty()); assertThrows(IllegalArgumentException.class, () -> ShortestRouteFinder.find(graph, "A", "X"));
    }
    @Test void tieBreakingIsStableAndDistancesUseLong() {
        var roads = List.of(RouteGraph.Road.between("A", "B", 1_000_000_000), RouteGraph.Road.between("B", "C", 1_000_000_000), RouteGraph.Road.between("C", "D", 1_000_000_000));
        assertEquals(3_000_000_000L, ShortestRouteFinder.find(graph(roads), "A", "D").orElseThrow().meters());
        var tied = graph(List.of(RouteGraph.Road.between("A", "B", 1), RouteGraph.Road.between("A", "C", 1), RouteGraph.Road.between("B", "D", 1), RouteGraph.Road.between("C", "D", 1)));
        assertEquals(List.of("A", "B", "D"), ShortestRouteFinder.find(tied, "A", "D").orElseThrow().path());
    }
    @Test void randomGraphsMatchFloydWarshallReference() {
        Random random = new Random(43);
        for (int round = 0; round < 20; round++) {
            List<RouteGraph.Road> roads = new ArrayList<>(); long[][] distances = new long[4][4];
            for (int i = 0; i < 4; i++) { Arrays.fill(distances[i], 1_000_000); distances[i][i] = 0; }
            for (int i = 0; i < 4; i++) for (int j = i + 1; j < 4; j++) if (random.nextBoolean()) {
                long meters = random.nextInt(1, 100); roads.add(RouteGraph.Road.between("" + (char)('A'+i), "" + (char)('A'+j), meters)); distances[i][j] = distances[j][i] = meters;
            }
            for (int k=0;k<4;k++) for(int i=0;i<4;i++) for(int j=0;j<4;j++) distances[i][j]=Math.min(distances[i][j],distances[i][k]+distances[k][j]);
            var graph = graph(roads);
            for (int i=0;i<4;i++) for(int j=0;j<4;j++) {
                var result = ShortestRouteFinder.find(graph,""+(char)('A'+i),""+(char)('A'+j));
                if (distances[i][j]==1_000_000) assertTrue(result.isEmpty()); else assertEquals(distances[i][j],result.orElseThrow().meters());
            }
        }
    }
}
