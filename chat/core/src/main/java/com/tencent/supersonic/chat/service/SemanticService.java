package com.tencent.supersonic.chat.service;


import static com.tencent.supersonic.common.pojo.Constants.DAY;
import static com.tencent.supersonic.common.pojo.Constants.DAY_FORMAT;
import static com.tencent.supersonic.common.pojo.Constants.DAY_FORMAT_INT;
import static com.tencent.supersonic.common.pojo.Constants.MONTH;
import static com.tencent.supersonic.common.pojo.Constants.MONTH_FORMAT;
import static com.tencent.supersonic.common.pojo.Constants.MONTH_FORMAT_INT;
import static com.tencent.supersonic.common.pojo.Constants.TIMES_FORMAT;
import static com.tencent.supersonic.common.pojo.Constants.TIME_FORMAT;
import static com.tencent.supersonic.common.pojo.Constants.WEEK;

import com.tencent.supersonic.auth.api.authentication.pojo.User;
import com.tencent.supersonic.chat.api.component.SemanticLayer;
import com.tencent.supersonic.chat.api.pojo.ModelSchema;
import com.tencent.supersonic.chat.api.pojo.SchemaElement;
import com.tencent.supersonic.chat.api.pojo.SemanticParseInfo;
import com.tencent.supersonic.chat.api.pojo.request.ChatAggConfigReq;
import com.tencent.supersonic.chat.api.pojo.request.ChatDefaultConfigReq;
import com.tencent.supersonic.chat.api.pojo.request.ChatDetailConfigReq;
import com.tencent.supersonic.chat.api.pojo.request.ItemVisibility;
import com.tencent.supersonic.chat.api.pojo.request.QueryFilter;
import com.tencent.supersonic.chat.api.pojo.response.AggregateInfo;
import com.tencent.supersonic.chat.api.pojo.response.ChatConfigResp;
import com.tencent.supersonic.chat.api.pojo.response.ChatConfigRichResp;
import com.tencent.supersonic.chat.api.pojo.response.ChatDefaultRichConfigResp;
import com.tencent.supersonic.chat.api.pojo.response.DataInfo;
import com.tencent.supersonic.chat.api.pojo.response.EntityInfo;
import com.tencent.supersonic.chat.api.pojo.response.MetricInfo;
import com.tencent.supersonic.chat.api.pojo.response.ModelInfo;
import com.tencent.supersonic.chat.config.AggregatorConfig;
import com.tencent.supersonic.chat.utils.ComponentFactory;
import com.tencent.supersonic.chat.utils.QueryReqBuilder;
import com.tencent.supersonic.common.pojo.DateConf;
import com.tencent.supersonic.common.pojo.DateConf.DateMode;
import com.tencent.supersonic.common.pojo.QueryColumn;
import com.tencent.supersonic.common.pojo.enums.AggOperatorEnum;
import com.tencent.supersonic.common.pojo.enums.RatioOverType;
import com.tencent.supersonic.common.util.ContextUtils;
import com.tencent.supersonic.common.util.DateUtils;
import com.tencent.supersonic.knowledge.service.SchemaService;
import com.tencent.supersonic.semantic.api.model.response.QueryResultWithSchemaResp;
import com.tencent.supersonic.semantic.api.query.enums.FilterOperatorEnum;
import com.tencent.supersonic.semantic.api.query.request.QueryStructReq;
import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@Slf4j
public class SemanticService {

    @Autowired
    private SchemaService schemaService;
    @Autowired
    private ConfigService configService;
    @Autowired
    private AggregatorConfig aggregatorConfig;

    private SemanticLayer semanticLayer = ComponentFactory.getSemanticLayer();

    public ModelSchema getModelSchema(Long id) {
        ModelSchema ModelSchema = schemaService.getModelSchema(id);
        if (!Objects.isNull(ModelSchema) && !Objects.isNull(ModelSchema.getModel())) {
            ChatConfigResp chaConfigInfo =
                    configService.fetchConfigByModelId(ModelSchema.getModel().getId());
            // filter dimensions in blacklist
            filterBlackDim(ModelSchema, chaConfigInfo);
            // filter metrics in blacklist
            filterBlackMetric(ModelSchema, chaConfigInfo);
        }

        return ModelSchema;
    }

    public EntityInfo getEntityInfo(SemanticParseInfo parseInfo, User user) {
        if (parseInfo != null && parseInfo.getModelId() > 0) {
            EntityInfo entityInfo = getEntityInfo(parseInfo.getModelId());
            if (parseInfo.getDimensionFilters().size() <= 0) {
                entityInfo.setMetrics(null);
                entityInfo.setDimensions(null);
                return entityInfo;
            }
            if (entityInfo.getModelInfo() != null && entityInfo.getModelInfo().getPrimaryEntityBizName() != null) {
                String ModelInfoPrimaryName = entityInfo.getModelInfo().getPrimaryEntityBizName();
                String ModelInfoId = "";
                for (QueryFilter chatFilter : parseInfo.getDimensionFilters()) {
                    if (chatFilter != null && chatFilter.getBizName() != null && chatFilter.getBizName()
                            .equals(ModelInfoPrimaryName)) {
                        if (chatFilter.getOperator().equals(FilterOperatorEnum.EQUALS)) {
                            ModelInfoId = chatFilter.getValue().toString();
                        }
                    }
                }
                if (!"".equals(ModelInfoId)) {
                    try {
                        setMainModel(entityInfo, parseInfo.getModelId(),
                                ModelInfoId, user);

                        return entityInfo;
                    } catch (Exception e) {
                        log.error("setMaintModel error {}", e);
                    }
                }
            }
        }
        return null;
    }

    public EntityInfo getEntityInfo(Long Model) {
        ChatConfigRichResp chaConfigRichDesc = configService.getConfigRichInfo(Model);
        if (Objects.isNull(chaConfigRichDesc) || Objects.isNull(chaConfigRichDesc.getChatDetailRichConfig())) {
            return new EntityInfo();
        }
        return getEntityInfo(chaConfigRichDesc);
    }

    private EntityInfo getEntityInfo(ChatConfigRichResp chaConfigRichDesc) {

        EntityInfo entityInfo = new EntityInfo();
        Long modelId = chaConfigRichDesc.getModelId();
        if (Objects.nonNull(chaConfigRichDesc) && Objects.nonNull(modelId)) {
            SemanticService schemaService = ContextUtils.getBean(SemanticService.class);
            ModelSchema ModelSchema = schemaService.getModelSchema(modelId);
            if (Objects.isNull(ModelSchema) || Objects.isNull(ModelSchema.getEntity())) {
                return entityInfo;
            }
            ModelInfo ModelInfo = new ModelInfo();
            ModelInfo.setItemId(modelId.intValue());
            ModelInfo.setName(ModelSchema.getModel().getName());
            ModelInfo.setWords(ModelSchema.getModel().getAlias());
            ModelInfo.setBizName(ModelSchema.getModel().getBizName());
            if (Objects.nonNull(ModelSchema.getEntity())) {
                ModelInfo.setPrimaryEntityBizName(ModelSchema.getEntity().getBizName());
            }

            entityInfo.setModelInfo(ModelInfo);
            List<DataInfo> dimensions = new ArrayList<>();
            List<DataInfo> metrics = new ArrayList<>();

            if (Objects.nonNull(chaConfigRichDesc) && Objects.nonNull(chaConfigRichDesc.getChatDetailRichConfig())
                    && Objects.nonNull(chaConfigRichDesc.getChatDetailRichConfig().getChatDefaultConfig())) {
                ChatDefaultRichConfigResp chatDefaultConfig = chaConfigRichDesc.getChatDetailRichConfig()
                        .getChatDefaultConfig();
                if (!CollectionUtils.isEmpty(chatDefaultConfig.getDimensions())) {
                    for (SchemaElement dimensionDesc : chatDefaultConfig.getDimensions()) {
                        DataInfo mainEntityDimension = new DataInfo();
                        mainEntityDimension.setItemId(dimensionDesc.getId().intValue());
                        mainEntityDimension.setName(dimensionDesc.getName());
                        mainEntityDimension.setBizName(dimensionDesc.getBizName());
                        dimensions.add(mainEntityDimension);
                    }
                    entityInfo.setDimensions(dimensions);
                }

                if (!CollectionUtils.isEmpty(chatDefaultConfig.getMetrics())) {
                    for (SchemaElement metricDesc : chatDefaultConfig.getMetrics()) {
                        DataInfo dataInfo = new DataInfo();
                        dataInfo.setName(metricDesc.getName());
                        dataInfo.setBizName(metricDesc.getBizName());
                        dataInfo.setItemId(metricDesc.getId().intValue());
                        metrics.add(dataInfo);
                    }
                    entityInfo.setMetrics(metrics);
                }
            }
        }
        return entityInfo;
    }

    public void setMainModel(EntityInfo ModelInfo, Long Model, String entity, User user) {
        ModelSchema ModelSchema = schemaService.getModelSchema(Model);

        ModelInfo.setEntityId(entity);
        SemanticParseInfo semanticParseInfo = new SemanticParseInfo();
        semanticParseInfo.setModel(ModelSchema.getModel());
        semanticParseInfo.setNativeQuery(true);
        semanticParseInfo.setMetrics(getMetrics(ModelInfo));
        semanticParseInfo.setDimensions(getDimensions(ModelInfo));
        DateConf dateInfo = new DateConf();
        int unit = 1;
        ChatConfigResp chatConfigInfo =
                configService.fetchConfigByModelId(ModelSchema.getModel().getId());
        if (Objects.nonNull(chatConfigInfo) && Objects.nonNull(chatConfigInfo.getChatDetailConfig())
                && Objects.nonNull(chatConfigInfo.getChatDetailConfig().getChatDefaultConfig())) {
            ChatDefaultConfigReq chatDefaultConfig = chatConfigInfo.getChatDetailConfig().getChatDefaultConfig();
            unit = chatDefaultConfig.getUnit();
            String date = LocalDate.now().plusDays(-unit).toString();
            dateInfo.setDateMode(DateMode.BETWEEN);
            dateInfo.setStartDate(date);
            dateInfo.setEndDate(date);
        } else {
            dateInfo.setUnit(unit);
            dateInfo.setDateMode(DateMode.RECENT);
        }
        semanticParseInfo.setDateInfo(dateInfo);

        // add filter
        QueryFilter chatFilter = new QueryFilter();
        chatFilter.setValue(String.valueOf(entity));
        chatFilter.setOperator(FilterOperatorEnum.EQUALS);
        chatFilter.setBizName(getEntityPrimaryName(ModelInfo));
        Set<QueryFilter> chatFilters = new LinkedHashSet();
        chatFilters.add(chatFilter);
        semanticParseInfo.setDimensionFilters(chatFilters);

        QueryResultWithSchemaResp queryResultWithColumns = null;
        try {
            queryResultWithColumns = semanticLayer.queryByStruct(QueryReqBuilder.buildStructReq(semanticParseInfo),
                    user);
        } catch (Exception e) {
            log.warn("setMainModel queryByStruct error, e:", e);
        }

        if (queryResultWithColumns != null) {
            if (!CollectionUtils.isEmpty(queryResultWithColumns.getResultList())
                    && queryResultWithColumns.getResultList().size() > 0) {
                Map<String, Object> result = queryResultWithColumns.getResultList().get(0);
                for (Map.Entry<String, Object> entry : result.entrySet()) {
                    String entryKey = getEntryKey(entry);
                    if (entry.getValue() == null || entryKey == null) {
                        continue;
                    }
                    ModelInfo.getDimensions().stream().filter(i -> entryKey.equals(i.getBizName()))
                            .forEach(i -> i.setValue(entry.getValue().toString()));
                    ModelInfo.getMetrics().stream().filter(i -> entryKey.equals(i.getBizName()))
                            .forEach(i -> i.setValue(entry.getValue().toString()));
                }
            }
        }
    }

    private Set<SchemaElement> getDimensions(EntityInfo ModelInfo) {
        Set<SchemaElement> dimensions = new LinkedHashSet();
        for (DataInfo mainEntityDimension : ModelInfo.getDimensions()) {
            SchemaElement dimension = new SchemaElement();
            dimension.setBizName(mainEntityDimension.getBizName());
            dimensions.add(dimension);
        }
        return dimensions;
    }

    private String getEntryKey(Map.Entry<String, Object> entry) {
        // metric parser special handle, TODO delete
        String entryKey = entry.getKey();
        if (entryKey.contains("__")) {
            entryKey = entryKey.split("__")[1];
        }
        return entryKey;
    }

    private Set<SchemaElement> getMetrics(EntityInfo ModelInfo) {
        Set<SchemaElement> metrics = new LinkedHashSet();
        for (DataInfo metricValue : ModelInfo.getMetrics()) {
            SchemaElement metric = new SchemaElement();
            BeanUtils.copyProperties(metricValue, metric);
            metrics.add(metric);
        }
        return metrics;
    }

    private String getEntityPrimaryName(EntityInfo ModelInfo) {
        return ModelInfo.getModelInfo().getPrimaryEntityBizName();
    }

    private void filterBlackMetric(ModelSchema ModelSchema, ChatConfigResp chaConfigInfo) {
        ItemVisibility visibility = generateFinalVisibility(chaConfigInfo);
        if (Objects.nonNull(chaConfigInfo) && Objects.nonNull(visibility)
                && !CollectionUtils.isEmpty(visibility.getBlackMetricIdList())
                && !CollectionUtils.isEmpty(ModelSchema.getMetrics())) {
            Set<SchemaElement> metric4Chat = ModelSchema.getMetrics().stream()
                    .filter(metric -> !visibility.getBlackMetricIdList().contains(metric.getId()))
                    .collect(Collectors.toSet());
            ModelSchema.setMetrics(metric4Chat);
        }
    }

    private void filterBlackDim(ModelSchema ModelSchema, ChatConfigResp chatConfigInfo) {
        ItemVisibility visibility = generateFinalVisibility(chatConfigInfo);
        if (Objects.nonNull(chatConfigInfo) && Objects.nonNull(visibility)
                && !CollectionUtils.isEmpty(visibility.getBlackDimIdList())
                && !CollectionUtils.isEmpty(ModelSchema.getDimensions())) {
            Set<SchemaElement> dim4Chat = ModelSchema.getDimensions().stream()
                    .filter(dim -> !visibility.getBlackDimIdList().contains(dim.getId()))
                    .collect(Collectors.toSet());
            ModelSchema.setDimensions(dim4Chat);
        }
    }

    private ItemVisibility generateFinalVisibility(ChatConfigResp chatConfigInfo) {
        ItemVisibility visibility = new ItemVisibility();

        ChatAggConfigReq chatAggConfig = chatConfigInfo.getChatAggConfig();
        ChatDetailConfigReq chatDetailConfig = chatConfigInfo.getChatDetailConfig();

        // both black is exist
        if (Objects.nonNull(chatAggConfig) && Objects.nonNull(chatAggConfig.getVisibility())
                && Objects.nonNull(chatDetailConfig) && Objects.nonNull(chatDetailConfig.getVisibility())) {
            List<Long> blackDimIdList = new ArrayList<>();
            blackDimIdList.addAll(chatAggConfig.getVisibility().getBlackDimIdList());
            blackDimIdList.retainAll(chatDetailConfig.getVisibility().getBlackDimIdList());
            List<Long> blackMetricIdList = new ArrayList<>();

            blackMetricIdList.addAll(chatAggConfig.getVisibility().getBlackMetricIdList());
            blackMetricIdList.retainAll(chatDetailConfig.getVisibility().getBlackMetricIdList());

            visibility.setBlackDimIdList(blackDimIdList);
            visibility.setBlackMetricIdList(blackMetricIdList);
        }
        return visibility;
    }

    public AggregateInfo getAggregateInfo(User user, SemanticParseInfo semanticParseInfo,
            QueryResultWithSchemaResp result) {
        if (CollectionUtils.isEmpty(semanticParseInfo.getMetrics()) || !aggregatorConfig.getEnableRatio()) {
            return new AggregateInfo();
        }
        List<String> resultMetricNames = result.getColumns().stream().map(c -> c.getNameEn())
                .collect(Collectors.toList());
        Optional<SchemaElement> ratioMetric = semanticParseInfo.getMetrics().stream()
                .filter(m -> resultMetricNames.contains(m.getBizName())).findFirst();
        if (ratioMetric.isPresent()) {
            AggregateInfo aggregateInfo = new AggregateInfo();
            MetricInfo metricInfo = new MetricInfo();
            metricInfo.setStatistics(new HashMap<>());
            try {
                String dateField = QueryReqBuilder.getDateField(semanticParseInfo.getDateInfo());

                Optional<String> lastDayOp = result.getResultList().stream().filter(r -> r.containsKey(dateField))
                        .map(r -> r.get(dateField).toString())
                        .sorted(Comparator.reverseOrder()).findFirst();
                if (lastDayOp.isPresent()) {
                    Optional<Map<String, Object>> lastValue = result.getResultList().stream()
                            .filter(r -> r.get(dateField).toString().equals(lastDayOp.get())).findFirst();
                    if (lastValue.isPresent() && lastValue.get().containsKey(ratioMetric.get().getBizName())) {
                        DecimalFormat df = new DecimalFormat("#.####");
                        metricInfo.setValue(df.format(lastValue.get().get(ratioMetric.get().getBizName())));
                    }
                    metricInfo.setDate(lastValue.get().get(dateField).toString());
                }
                CompletableFuture<MetricInfo> metricInfoRoll = CompletableFuture
                        .supplyAsync(() -> {
                            return queryRatio(user, semanticParseInfo, ratioMetric.get(), AggOperatorEnum.RATIO_ROLL,
                                    result);
                        });
                CompletableFuture<MetricInfo> metricInfoOver = CompletableFuture
                        .supplyAsync(() -> {
                            return queryRatio(user, semanticParseInfo, ratioMetric.get(), AggOperatorEnum.RATIO_OVER,
                                    result);
                        });
                CompletableFuture.allOf(metricInfoRoll, metricInfoOver);
                metricInfo.setName(metricInfoRoll.get().getName());
                metricInfo.setValue(metricInfoRoll.get().getValue());
                metricInfo.getStatistics().putAll(metricInfoRoll.get().getStatistics());
                metricInfo.getStatistics().putAll(metricInfoOver.get().getStatistics());
                aggregateInfo.getMetricInfos().add(metricInfo);
            } catch (Exception e) {
                log.error("queryRatio error {}", e);
            }
            return aggregateInfo;
        }
        return new AggregateInfo();
    }

    private MetricInfo queryRatio(User user, SemanticParseInfo semanticParseInfo, SchemaElement metric,
            AggOperatorEnum aggOperatorEnum, QueryResultWithSchemaResp results) {
        MetricInfo metricInfo = new MetricInfo();
        metricInfo.setStatistics(new HashMap<>());
        QueryStructReq queryStructReq = QueryReqBuilder.buildStructRatioReq(semanticParseInfo, metric, aggOperatorEnum);
        DateConf dateInfo = semanticParseInfo.getDateInfo();
        String dateField = QueryReqBuilder.getDateField(dateInfo);

        queryStructReq.setGroups(new ArrayList<>(Arrays.asList(dateField)));
        queryStructReq.setDateInfo(getRatioDateConf(aggOperatorEnum, semanticParseInfo, results));
        QueryResultWithSchemaResp queryResp = semanticLayer.queryByStruct(queryStructReq, user);
        if (Objects.nonNull(queryResp) && !CollectionUtils.isEmpty(queryResp.getResultList())) {

            Map<String, Object> result = queryResp.getResultList().get(0);
            Optional<QueryColumn> valueColumn = queryResp.getColumns().stream()
                    .filter(c -> c.getNameEn().equals(metric.getBizName())).findFirst();
            if (valueColumn.isPresent()) {

                String valueField = String.format("%s_%s", valueColumn.get().getNameEn(),
                        aggOperatorEnum.getOperator());
                if (result.containsKey(valueColumn.get().getNameEn())) {
                    DecimalFormat df = new DecimalFormat("#.####");
                    metricInfo.setValue(df.format(result.get(valueColumn.get().getNameEn())));
                }
                String ratio = "";
                if (Objects.nonNull(result.get(valueField))) {
                    ratio = String.format("%.2f",
                            (Double.valueOf(result.get(valueField).toString()) * 100)) + "%";
                }
                String statisticsRollName = RatioOverType.DAY_ON_DAY.getShowName();
                String statisticsOverName = RatioOverType.WEEK_ON_DAY.getShowName();
                if (MONTH.equals(dateInfo.getPeriod())) {
                    statisticsRollName = RatioOverType.MONTH_ON_MONTH.getShowName();
                    statisticsOverName = RatioOverType.YEAR_ON_MONTH.getShowName();
                }
                if (WEEK.equals(dateInfo.getPeriod())) {
                    statisticsRollName = RatioOverType.WEEK_ON_WEEK.getShowName();
                    statisticsOverName = RatioOverType.MONTH_ON_WEEK.getShowName();
                }
                metricInfo.getStatistics().put(aggOperatorEnum.equals(AggOperatorEnum.RATIO_ROLL) ? statisticsRollName
                                : statisticsOverName,
                        ratio);
            }
            metricInfo.setName(metric.getName());
        }
        return metricInfo;
    }

    private DateConf getRatioDateConf(AggOperatorEnum aggOperatorEnum, SemanticParseInfo semanticParseInfo,
            QueryResultWithSchemaResp results) {
        String dateField = QueryReqBuilder.getDateField(semanticParseInfo.getDateInfo());
        Optional<String> lastDayOp = results.getResultList().stream()
                .map(r -> r.get(dateField).toString())
                .sorted(Comparator.reverseOrder()).findFirst();
        if (lastDayOp.isPresent()) {
            String lastDay = lastDayOp.get();
            DateConf dateConf = new DateConf();
            dateConf.setPeriod(semanticParseInfo.getDateInfo().getPeriod());
            dateConf.setDateMode(DateMode.LIST);
            List<String> dayList = new ArrayList<>();
            dayList.add(lastDay);
            String start = "";
            if (DAY.equalsIgnoreCase(semanticParseInfo.getDateInfo().getPeriod())) {
                DateTimeFormatter formatter = DateUtils.getDateFormatter(lastDay,
                        new String[]{DAY_FORMAT, DAY_FORMAT_INT});
                LocalDate end = LocalDate.parse(lastDay, formatter);
                start = aggOperatorEnum.equals(AggOperatorEnum.RATIO_ROLL) ? end.minusDays(1).format(formatter)
                        : end.minusWeeks(1).format(formatter);
            }
            if (WEEK.equalsIgnoreCase(semanticParseInfo.getDateInfo().getPeriod())) {
                DateTimeFormatter formatter = DateUtils.getTimeFormatter(lastDay,
                        new String[]{TIMES_FORMAT, DAY_FORMAT, TIME_FORMAT, DAY_FORMAT_INT});
                LocalDateTime end = LocalDateTime.parse(lastDay, formatter);
                start = aggOperatorEnum.equals(AggOperatorEnum.RATIO_ROLL) ? end.minusWeeks(1).format(formatter)
                        : end.minusMonths(1).with(DayOfWeek.MONDAY).format(formatter);
            }
            if (MONTH.equalsIgnoreCase(semanticParseInfo.getDateInfo().getPeriod())) {
                DateTimeFormatter formatter = DateUtils.getDateFormatter(lastDay,
                        new String[]{MONTH_FORMAT, MONTH_FORMAT_INT});
                YearMonth end = YearMonth.parse(lastDay, formatter);
                start = aggOperatorEnum.equals(AggOperatorEnum.RATIO_ROLL) ? end.minusMonths(1).format(formatter)
                        : end.minusYears(1).format(formatter);
            }
            dayList.add(start);
            dateConf.setDateList(dayList);
            return dateConf;

        }
        return semanticParseInfo.getDateInfo();
    }
}