package org.example;


import com.google.gson.*;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final static TimeUnit DEFAULT_TIME_UNIT = TimeUnit.MINUTES;

    private final static int DEFAULT_REQUEST_LIMIT = 20;

    private final static String URL = "https://markirovka.demo.crpt.tech/";

    private final HttpClient httpClient = Config.httpClient();

    private final  Gson gson = Config.getGson();

    private final TimeUnit timeUnit;

    private final Lock lock = new ReentrantLock();

    private final int requestLimit;

    private final AtomicInteger requestCount = new AtomicInteger(0);

    public CrptApi(TimeUnit timeUnit, int requestLimit){
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        start();
    }

    public CrptApi(){
        this(DEFAULT_TIME_UNIT, DEFAULT_REQUEST_LIMIT);
    }

    private void start(){
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(timeUnit.toChronoUnit().getDuration().toMillis());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                requestCount.updateAndGet(n -> 0);
            }
        });
        thread.start();
    }
    private void newRequest(){
        if (requestCount.updateAndGet(n -> n + 1) > requestLimit) {
            lock.lock();
            try{
                while(requestCount.updateAndGet(n -> n) > requestLimit){}
            }finally {
                lock.unlock();
                requestCount.updateAndGet(n -> n + 1);
            }
        }
    }


    public RollOutReportResponse rollOutReport(RollOutReport body, String omsId) throws BadRequestException {
        newRequest();
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder().
                    uri(new URI(URL + "/api/v2/{extension}/rollout?omsId=" + omsId))
                    .version(HttpClient.Version.HTTP_1_1)
                    .headers("Accept", "application/json")
                    .headers("clientToken", "1cecc8fb-fb47-4c8a-af3d-d34c1ead8c4f")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body))).
                    build();

            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new BadRequestException("bad request" + response.statusCode() + " "+ response.body());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new BadRequestException("bad request" + e);
        }

        return gson.fromJson(response.body(), RollOutReportResponse.class);
    }

    static class BadRequestException extends Exception {
        public BadRequestException(String s) {
            super(s);
        }
    }

    static class RollOutReportResponse{
        private UUID omsId;
        private UUID reportId;
    }

    static class RollOutReport {
        private String usageType;
        private String documentFormat;
        private Type type;
        private String participantInn;
        private LocalDate productionDate;
        private List<Product> products;
        private Produced produced;
        @SerializedName("import")
        private Import anImport;

        public RollOutReport(String usageType, String documentFormat, Type type, String participantInn, LocalDate productionDate, List<Product> products, Produced produced) {
            this(usageType, documentFormat, type, participantInn, productionDate, products);
            if (type != Type.LP_GOODS_IMPORT_AUTO){
                throw new IllegalArgumentException("import cannot be null if type = " + type);
            }
            if (produced == null) {
                throw new IllegalArgumentException("Produced can`t be bull");
            }
            this.produced = produced;
        }

        public RollOutReport(String usageType, String documentFormat, Type type, String participantInn, LocalDate productionDate, List<Product> products, Import anImport) {
            this(usageType, documentFormat, type, participantInn, productionDate, products);
            if (type != Type.LP_INTRODUCE_GOODS_AUTO){
                throw new IllegalArgumentException("produced cannot be null if type = " + type);
            }
            if (anImport == null) {
                throw new IllegalArgumentException("Import can`t be bull");
            }
            this.anImport = anImport;
        }

        private RollOutReport(String usageType, String documentFormat, Type type, String participantInn, LocalDate productionDate, List<Product> products) {
            this.usageType = usageType;
            if (!Objects.equals(documentFormat, "MANUAL")){
                throw new IllegalArgumentException("The value of the document format should be MANUAL");
            }
            this.documentFormat = documentFormat;
            this.type = type;
            if (participantInn == null || participantInn.length() != 10 && participantInn.length() != 12) {
                throw new IllegalArgumentException("inn length should be 10 or 12");
            }
            this.participantInn = participantInn;
            if (productionDate.isBefore(LocalDate.now().minusYears(5))
                    || productionDate.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("production date should be between today and 5 year ago");
            }
            this.productionDate = productionDate;
            if (products == null || products.size() == 0) {
                throw new IllegalArgumentException("products must have at least 1 element");
            }
            this.products = products;
        }
    }

    static class RollOutReportForMilk extends RollOutReport {
        private String accompanyingDocument;
        private String expDate;
        private String expDate72;
        private Double capacity;

        private RollOutReportForMilk(String usageType, String documentFormat, Type type, String participantInn, LocalDate productionDate, List<Product> products, Produced produced) {
            super(usageType, documentFormat, type, participantInn, productionDate, products, produced);
        }

        private RollOutReportForMilk(String usageType, String documentFormat, Type type, String participantInn, LocalDate productionDate, List<Product> products, Import anImport) {
            super(usageType, documentFormat, type, participantInn, productionDate, products, anImport);
        }


        private RollOutReportForMilk(String usageType, String documentFormat, Type type, String participantInn, LocalDate productionDate, List<Product> products, String accompanyingDocument, String expDate, String expDate72, Double capacity) {
            super(usageType, documentFormat, type, participantInn, productionDate, products);
            if (accompanyingDocument == null) {
                throw new IllegalArgumentException("accompanyingDocument can`t be null");
            }
            this.accompanyingDocument = accompanyingDocument;
            if (expDate == null && expDate72 == null
                    || expDate != null && expDate72 != null) {
                throw new IllegalArgumentException("you must specify expDate or expDate72");
            }
            if (expDate != null && expDate.length() != 6) {
                throw new IllegalArgumentException("invalid expDate");
            }
            this.expDate = expDate;

            if (expDate72 != null && expDate72.length() != 10) {
                throw new IllegalArgumentException("invalid expDate");
            }
            this.expDate72 = expDate72;
            this.capacity = capacity;
        }

        public RollOutReportForMilk(String usageType, String documentFormat, Type type, String participantInn, LocalDate productionDate, List<Product> products, Import anImport, String accompanyingDocument, String expDate, String expDate72, Double capacity) {
            this(usageType, documentFormat, type, participantInn, productionDate, products, accompanyingDocument, expDate, expDate72, capacity);
            if (type != Type.LP_INTRODUCE_GOODS_AUTO){
                throw new IllegalArgumentException("produced cannot be null if type = " + type);
            }
            if (anImport == null) {
                throw new IllegalArgumentException("Import can`t be bull");
            }
            super.anImport = anImport;
        }

        public RollOutReportForMilk(String usageType, String documentFormat, Type type, String participantInn, LocalDate productionDate, List<Product> products, Produced produced, String accompanyingDocument, String expDate, String expDate72, Double capacity) {
            this(usageType, documentFormat, type, participantInn, productionDate, products, accompanyingDocument, expDate, expDate72, capacity);
            if (type != Type.LP_GOODS_IMPORT_AUTO){
                throw new IllegalArgumentException("import cannot be null if type = " + type);
            }
            if (produced == null) {
                throw new IllegalArgumentException("Produced can`t be bull");
            }
            super.produced = produced;
        }
    }

    static class Product{
        private String code;
        private String certificateDocument;
        private LocalDate certificateDocumentDate;
        private String certificateDocumentNumber;
        private String tnvedCode;

        public Product(String code, String certificateDocument, LocalDate certificateDocumentDate, String certificateDocumentNumber, String tnvedCode) {
            if (code == null) {
                throw new IllegalArgumentException("code can`t be null");
            }
            this.code = code;
            if (!certificateDocument.equals("1") && !certificateDocument.equals("2")) {
                throw new IllegalArgumentException("certificate document value should be 1 or 2");
            }
            this.certificateDocument = certificateDocument;
            if (certificateDocumentDate.isBefore(LocalDate.now().minusYears(5))
                    || certificateDocumentDate.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("production date should be between today and 5 year ago");
            }
            this.certificateDocumentDate = certificateDocumentDate;
            if (certificateDocumentNumber == null){
                throw new IllegalArgumentException("certificateDocumentNumber can`t be null");
            }
            this.certificateDocumentNumber = certificateDocumentNumber;
            if (tnvedCode == null || tnvedCode.length() != 10) {
                throw new IllegalArgumentException("invalid tnved code");
            }
            this.tnvedCode = tnvedCode;
        }
    }

    static class Produced{
        private String producerInn;
        private String ownerInn;
        private String productionType;

        public Produced(String producerInn, String ownerInn, String productionType) {
            if (producerInn == null || producerInn.length() != 10 && producerInn.length() != 12) {
                throw new IllegalArgumentException("inn length should be 10 or 12");
            }
            this.producerInn = producerInn;
            if (ownerInn == null || ownerInn.length() != 10 && ownerInn.length() != 12) {
                throw new IllegalArgumentException("inn length should be 10 or 12");
            }
            this.ownerInn = ownerInn;
            if (productionType == null || !productionType.equals("OWN_PRODUCTION")) {
                throw new IllegalArgumentException("production type should be OWN_PRODUCTION");
            }
            this.productionType = productionType;
        }
    }

    static class Import{
        private LocalDate declarationDate;
        private String declarationNumber;
        private String customsCode;
        private long decisionCode;

        public Import(LocalDate declarationDate, String declarationNumber, String customsCode, long decisionCode) {
            if (declarationDate.isBefore(LocalDate.now().minusYears(5))
                    || declarationDate.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("production date should be between today and 5 year ago");
            }
            this.declarationDate = declarationDate;
            if (declarationNumber == null){
                throw new IllegalArgumentException("declarationNumber can`t be null");
            }
            this.declarationNumber = declarationNumber;
            if (customsCode == null){
                throw new IllegalArgumentException("customsCode can`t be null");
            }
            this.customsCode = customsCode;
            if (decisionCode < 0){
                throw new IllegalArgumentException("invalid decisionCode");
            }
            this.decisionCode = decisionCode;
        }
    }

    enum Type{
        LP_INTRODUCE_GOODS_AUTO("LP_INTRODUCE_GOODS_AUTO"),
        LP_GOODS_IMPORT_AUTO("LP_GOODS_IMPORT_AUTO");

        private final String name;

        Type(String name){
            this.name = name;
        }
    }

    static class Config{
        static public HttpClient httpClient(){
            return HttpClient.newBuilder().build();
        }

        static public Gson getGson(){
            return new GsonBuilder()
                            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                            .setPrettyPrinting()
                            .create();
        }
    }

    static class LocalDateAdapter implements JsonSerializer<LocalDate> {
        @Override
        public JsonElement serialize(LocalDate date, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(date.format(DateTimeFormatter.ISO_LOCAL_DATE)); // "yyyy-mm-dd"
        }

    }

    public static void main(String[] args) {
        CrptApi api = new CrptApi();
        RollOutReport ror = new RollOutReport("SENT_TO_PRINTER",
                "MANUAL",
                Type.LP_GOODS_IMPORT_AUTO,
                "1334567890",
                LocalDate.parse("2019-10-10"),
                List.of(
                        new Product("01046071128147902154BkTTHqlQl9E\u001d17190516\u001d93ZmFrZQ==",
                                "1",
                                LocalDate.parse("2018-10-13"),
                                "1234",
                                "1111111111"
                        )),
                new Produced("1134567890","1234567890","OWN_PRODUCTION"));
        try {
            api.rollOutReport(ror, "123456789");
        } catch (BadRequestException e) {
            throw new RuntimeException(e);
        }
    }
}
