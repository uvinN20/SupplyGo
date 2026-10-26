package com.supplygo.service;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.supplygo.model.*;
import com.supplygo.storage.RouteRepository;

class RouteServiceTest {
    @TempDir Path directory; final UserProfile admin = new UserProfile("admin",Role.ADMIN);
    @Test void updatesAndRestartPreserveRoadsAndNames() throws Exception {
        try(var repo=new RouteRepository(directory)) {
            var service=new RouteService(repo); service.location(admin,"A","Matara");service.location(admin,"B","Galle");service.road(admin,"B","A",50000);service.road(admin,"A","B",45000);service.location(admin,"A","Matara town");
            assertEquals(45000,service.find(admin,"A","B").meters());assertEquals(1,repo.snapshot().roads().size());
        }
        try(var repo=new RouteRepository(directory)){assertEquals(45000,new RouteService(repo).find(admin,"A","B").meters());assertEquals("Matara town",repo.snapshot().locations().getFirst().name());}
    }
    @Test void allRolesReadButOnlyAdminAndProductionManagerWrite() throws Exception {
        try(var repo=new RouteRepository(directory)) {
            var service=new RouteService(repo);service.location(admin,"A","A");
            for(Role role:Role.values()) {
                var actor=new UserProfile("account",role);assertNotNull(service.page(actor,false,0));assertEquals(0,service.find(actor,"A","A").meters());
                if(role==Role.ADMIN||role==Role.PRODUCTION_MANAGER)service.location(actor,"A","A");
                else {assertEquals("FORBIDDEN",assertThrows(AuthException.class,()->service.location(actor,"B","B")).code());assertThrows(AuthException.class,()->service.road(actor,"A","B",10));}
            }
            assertThrows(AuthException.class,()->service.find(null,"A","A"));assertThrows(AuthException.class,()->service.page(null,false,0));
        }
    }
    @Test void invalidEdgesAndLocationsDoNotMutateGraph() throws Exception {
        try(var repo=new RouteRepository(directory)) {
            var s=new RouteService(repo);s.location(admin,"A","A");s.location(admin,"B","B");var before=repo.snapshot();
            for(long distance:new long[]{-1,0,1_000_000_001})assertEquals("INVALID_ROUTE",assertThrows(AuthException.class,()->s.road(admin,"A","B",distance)).code());
            assertThrows(AuthException.class,()->s.road(admin,"A","A",10));assertThrows(AuthException.class,()->s.road(admin,"A","X",10));assertThrows(AuthException.class,()->s.location(admin,"bad id","Name"));
            assertThrows(AuthException.class,()->s.location(admin,"C","x".repeat(65)));assertEquals(before,repo.snapshot());assertEquals("ROUTE_NOT_FOUND",assertThrows(AuthException.class,()->s.find(admin,"A","B")).code());
        }
    }
    @Test void pagingAndConcurrentUpdatesPreserveAllNodes() throws Exception {
        try(var repo=new RouteRepository(directory);var pool=Executors.newFixedThreadPool(8)) {
            var s=new RouteService(repo);List<Callable<Void>> work=new ArrayList<>();
            for(int i=0;i<25;i++){final int id=i;work.add(()->{s.location(admin,"L%02d".formatted(id),"Location "+id);return null;});}
            for(var result:pool.invokeAll(work))result.get();assertEquals(25,repo.snapshot().locations().size());
            assertEquals(10,s.page(admin,false,0).get("nextOffset").getAsInt());assertEquals(5,s.page(admin,false,20).getAsJsonArray("locations").size());assertEquals(-1,s.page(admin,false,20).get("nextOffset").getAsInt());
            assertThrows(AuthException.class,()->s.page(admin,false,-1));assertThrows(AuthException.class,()->s.page(admin,false,1001));
        }
    }
    @Test void failedReplaceKeepsMemoryAndCleansTemporaryFile() throws Exception {
        try(var repo=new RouteRepository(directory)) {
            var s=new RouteService(repo);s.location(admin,"A","A");var before=repo.snapshot();Path file=directory.resolve("routes.json");Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("block"),"block");
            assertEquals("STORAGE_ERROR",assertThrows(AuthException.class,()->s.location(admin,"B","B")).code());assertEquals(before,repo.snapshot());
            try(var files=Files.list(directory)){assertFalse(files.anyMatch(p->p.toString().endsWith(".tmp")));}
        }
    }
    @Test void corruptDataAndSecondProcessFailClosed() throws Exception {
        try(var repo=new RouteRepository(directory)){assertThrows(java.io.IOException.class,()->new RouteRepository(directory));}
        for(String json:List.of("null","{}","{\"version\":2,\"locations\":[],\"roads\":[]}","{\"version\":1,\"locations\":[],\"roads\":[{\"from\":\"A\",\"to\":\"B\",\"meters\":1}]}")) {
            Files.writeString(directory.resolve("routes.json"),json);assertThrows(java.io.IOException.class,()->new RouteRepository(directory));
        }
    }
}
