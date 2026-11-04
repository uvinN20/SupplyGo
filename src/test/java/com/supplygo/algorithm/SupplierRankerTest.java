package com.supplygo.algorithm;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SupplierRankerTest {
    SupplierCandidate c(String name, int days, long price, int meters) { return new SupplierCandidate(name, days, price, meters); }
    @Test void freshnessThenPriceThenDistanceThenUsernameDeterminePriority() {
        var input = List.of(c("old", 1, 1, 0), c("expensive", 7, 300, 0), c("far", 7, 200, 200),
                c("zulu", 7, 200, 100), c("alpha", 7, 200, 100));
        assertEquals(List.of("alpha", "zulu", "far", "expensive", "old"), SupplierRanker.rank(input, 5).stream().map(SupplierCandidate::username).toList());
    }
    @Test void boundedHeapMatchesFullSortForRandomInputsAndSeveralLimits() {
        Random random = new Random(41); var input = new ArrayList<SupplierCandidate>();
        for (int i = 0; i < 1000; i++) input.add(c("farm" + i, random.nextInt(30), random.nextLong(1, 100000), random.nextInt(100000)));
        Collections.shuffle(input, random);
        for (int k : new int[]{1, 2, 10, 100})
            assertEquals(input.stream().sorted(SupplierRanker.BEST_FIRST).limit(k).toList(), SupplierRanker.rank(input, k));
    }
    @Test void emptyInputsLimitsAndInputImmutability() {
        assertTrue(SupplierRanker.rank(List.of(), 10).isEmpty());
        var original = List.of(c("farm", 3, 100, 10)); var result = SupplierRanker.rank(original, 100);
        assertEquals(original, result); assertThrows(UnsupportedOperationException.class, () -> result.clear());
        assertThrows(IllegalArgumentException.class, () -> SupplierRanker.rank(original, 0));
        assertThrows(IllegalArgumentException.class, () -> SupplierRanker.rank(original, 101));
        assertThrows(IllegalArgumentException.class, () -> SupplierRanker.rank(Collections.nCopies(10001, original.getFirst()), 1));
    }
    @Test void invalidFieldsNullsAndDuplicateSuppliersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> c("Bad Name", 3, 100, 10));
        assertThrows(IllegalArgumentException.class, () -> c("farm", -1, 100, 10));
        assertThrows(IllegalArgumentException.class, () -> c("farm", 3651, 100, 10));
        assertThrows(IllegalArgumentException.class, () -> c("farm", 1, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> c("farm", 1, Long.MAX_VALUE, 10));
        assertThrows(IllegalArgumentException.class, () -> c("farm", 1, 100, -1));
        assertThrows(NullPointerException.class, () -> SupplierRanker.rank(null, 1));
        assertThrows(NullPointerException.class, () -> SupplierRanker.rank(Arrays.asList((SupplierCandidate) null), 1));
        assertThrows(IllegalArgumentException.class, () -> SupplierRanker.rank(List.of(c("farm", 1, 10, 0), c("farm", 2, 20, 0)), 1));
    }
}
