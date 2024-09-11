package com.tencent.supersonic.headless;

import com.tencent.supersonic.auth.api.authentication.pojo.User;
import com.tencent.supersonic.headless.api.pojo.QueryDataType;
import com.tencent.supersonic.headless.api.pojo.request.QueryMapReq;
import com.tencent.supersonic.headless.api.pojo.response.MapInfoResp;
import com.tencent.supersonic.headless.server.service.MetaDiscoveryService;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;

public class MetaDiscoveryTest extends BaseTest {

    @Autowired
    protected MetaDiscoveryService metaDiscoveryService;

    @Test
    public void testGetMapMeta() throws Exception {
        QueryMapReq queryMapReq = new QueryMapReq();
        queryMapReq.setQueryText("对比alice和lucy的访问次数");
        queryMapReq.setTopN(10);
        queryMapReq.setUser(User.getFakeUser());
        queryMapReq.setDataSetNames(Collections.singletonList("超音数"));
        MapInfoResp mapMeta = metaDiscoveryService.getMapMeta(queryMapReq);

        Assertions.assertNotNull(mapMeta);
        Assertions.assertNotEquals(0, mapMeta.getDataSetMapInfo().get("超音数").getMapFields());
        Assertions.assertNotEquals(0, mapMeta.getDataSetMapInfo().get("超音数").getTopFields());
    }

    @Test
    public void testGetMapMeta2() throws Exception {
        QueryMapReq queryMapReq = new QueryMapReq();
        queryMapReq.setQueryText("风格为流行的艺人");
        queryMapReq.setTopN(10);
        queryMapReq.setUser(User.getFakeUser());
        queryMapReq.setDataSetNames(Collections.singletonList("艺人库"));
        queryMapReq.setQueryDataType(QueryDataType.TAG);
        MapInfoResp mapMeta = metaDiscoveryService.getMapMeta(queryMapReq);
        Assert.assertNotNull(mapMeta);
    }

    @Test
    public void testGetMapMeta3() throws Exception {
        QueryMapReq queryMapReq = new QueryMapReq();
        queryMapReq.setQueryText("超音数访问次数最高的部门");
        queryMapReq.setTopN(10);
        queryMapReq.setUser(User.getFakeUser());
        queryMapReq.setDataSetNames(Collections.singletonList("超音数"));
        queryMapReq.setQueryDataType(QueryDataType.METRIC);
        MapInfoResp mapMeta = metaDiscoveryService.getMapMeta(queryMapReq);
        Assert.assertNotNull(mapMeta);
    }
}
