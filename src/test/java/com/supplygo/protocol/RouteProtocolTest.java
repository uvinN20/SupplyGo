package com.supplygo.protocol;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import com.google.gson.*;

class RouteProtocolTest {
    @Test void fractionalNegativeAndForgedRoadPayloadsAreRejected() {
        for(String meters:new String[]{"-1","0","1.5","1e3","1000000001","\"10\""}) {
            var payload=JsonParser.parseString("{\"from\":\"A\",\"to\":\"B\",\"meters\":"+meters+"}").getAsJsonObject();
            assertThrows(ProtocolException.class,()->ProtocolCodec.decodeRequest(ProtocolCodec.encode(Request.create(MessageType.SAVE_ROAD,payload))));
        }
        JsonObject forged=new JsonObject();forged.addProperty("from","A");forged.addProperty("to","B");forged.addProperty("role","ADMIN");
        assertThrows(ProtocolException.class,()->ProtocolCodec.decodeRequest(ProtocolCodec.encode(Request.create(MessageType.FIND_ROUTE,forged))));
    }
    @Test void boundedPagesAndLongestPathFitFrames() throws Exception {
        JsonObject page=new JsonObject();JsonArray locations=new JsonArray();
        for(int i=0;i<10;i++){JsonObject row=new JsonObject();row.addProperty("id","LOCATION_"+i);row.addProperty("name","<".repeat(64));locations.add(row);}
        page.add("locations",locations);page.addProperty("nextOffset",10);
        String encoded=ProtocolCodec.encode(Response.ok("r".repeat(64),"ROUTE_LOCATIONS",page));FrameIO.write(new ByteArrayOutputStream(),encoded);ProtocolCodec.decodeResponse(encoded);
        JsonObject route=new JsonObject();JsonArray path=new JsonArray();for(int i=0;i<100;i++)path.add("X".repeat(20)+i);route.add("path",path);route.addProperty("meters",99_000_000_000L);
        encoded=ProtocolCodec.encode(Response.ok("id","ROUTE",route));FrameIO.write(new ByteArrayOutputStream(),encoded);ProtocolCodec.decodeResponse(encoded);
    }
    @Test void malformedResponsesAreRejected() {
        for(String message:new String[]{"ROUTE","ROUTE_LOCATIONS","ROUTE_ROADS"}) assertThrows(ProtocolException.class,()->ProtocolCodec.decodeResponse(ProtocolCodec.encode(Response.ok("id",message))));
        var data=JsonParser.parseString("{\"path\":[\"A\",\"A\"],\"meters\":10}").getAsJsonObject();
        assertThrows(ProtocolException.class,()->ProtocolCodec.decodeResponse(ProtocolCodec.encode(Response.ok("id","ROUTE",data))));
    }
}
