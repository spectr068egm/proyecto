import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.ArrayList;

// Azure IoT SDK
import com.microsoft.azure.sdk.iot.service.Message;
import com.microsoft.azure.sdk.iot.service.ServiceClient;
import com.microsoft.azure.sdk.iot.service.IotHubServiceClientProtocol;
import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceMethod;
import com.microsoft.azure.sdk.iot.service.devicetwin.MethodResult;

// ↓ MongoDB imports
import com.mongodb.client.*;
import org.bson.Document;


public class Main {
    private static final String IOTHUB_CONN_STR = "HostName=luces-proj.azure-devices.net;SharedAccessKeyName=service;SharedAccessKey=6/s3pOxRVZTkrtkeIJWRxfVxt9s4fXqweAIoTDaY7Lg=";
    private static final IotHubServiceClientProtocol IOTHUB_PROTOCOL = IotHubServiceClientProtocol.AMQPS;
    private static final String DEVICE_ID = "luces1";
    private static ServiceClient serviceClient;

    // ↓ MongoDB connection string (pone aquí tu URL de Atlas)
    private static final String MONGO_CONN_STR = "mongodb+srv://paulabustamante:pass12345678@progra.sjsreu5.mongodb.net/lucesdb?retryWrites=true&w=majority&appName=Progra";
    // ↓ Lista donde almacenaremos temporalmente los documentos descargados
    private static List<Document> telemetryData = new ArrayList<>();

    // Después:
    private static final String[] TABLE_COLUMNS = {
            "msgCount", "activo/inactivo", "Wats encendido", "W L sensor 1", "W L sensor 2", "dato5"
    };
    // índices de 'datos' que quieres mostrar:
    private static final int[] DATA_INDEXES = {0, 10, 11, 12, 6};

    private static final int LED_COUNT = 8;

    // true = verde, false = rojo
    private static final boolean[] LED_ON = {true, true, true, true, true, true, true, false};


    private static JLabel[] ledLabels = new JLabel[LED_COUNT];

    // indicadores de totales de columnas 2,3,4
    private static JLabel totalCol2Label;
    private static JLabel totalCol3Label;
    private static JLabel totalCol4Label;




    public static void main(String[] args) {
        // Conecta en paralelo a ambos servicios
        new Thread(Main::initAzureIoT).start();
        new Thread(Main::initMongoDB).start();        // ↓ Inicializa MongoDB
        SwingUtilities.invokeLater(Main::createAndShowGUI);
    }

    private static void initAzureIoT() {
        try {
            serviceClient = ServiceClient.createFromConnectionString(IOTHUB_CONN_STR, IOTHUB_PROTOCOL);
            serviceClient.open();
            System.out.println("[AzureIoT] Conectado a IoT Hub");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[AzureIoT] Error al conectar: " + e.getMessage());
        }
    }

    // ↓ Nuevo método: conecta a MongoDB Atlas y descarga todos los documentos
    private static void initMongoDB() {
        try (MongoClient mongoClient = MongoClients.create(MONGO_CONN_STR)) {
            MongoDatabase db = mongoClient.getDatabase("lucesdb");
            MongoCollection<Document> coll = db.getCollection("telemetria");
            FindIterable<Document> docs = coll.find();
            for (Document doc : docs) {
                telemetryData.add(doc);
                System.out.println("[MongoDB] Documento: " + doc.toJson());
            }
            System.out.println("[MongoDB] Descargados " + telemetryData.size() + " documentos.");
        } catch (Exception e) {
            System.err.println("[MongoDB] Error al conectar o descargar: " + e.getMessage());
        }
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Control de Dispositivo IoT");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);

        // 1) Crea el northPanel antes de usarlo
        JPanel northPanel = new JPanel(new BorderLayout());



// — Panel de botones —
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        northPanel.add(topPanel, BorderLayout.NORTH);


        // 1) Definición de nombres y botones (ya la tienes):
         final String[] BUTTON_NAMES = {
                "Reset", "Encender", "Apagar", "Activar S1", "Activar S2", "Apagar Luces Generales"
        };

        // 2) En createAndShowGUI(), a la hora de crear listeners:
        for (int i = 0; i < BUTTON_NAMES.length; i++) {
            final int idx = i + 1;
            JButton btn = new JButton(BUTTON_NAMES[i]);
            btn.addActionListener(e -> {
                switch (idx) {
                    case 1: handleReset(); break;
                    case 2: handleEncender(); break;
                    case 3: handleApagar(); break;
                    case 4: handleActivarS1(); break;
                    case 5: handleActivarS2(); break;
                    case 6: handleApagarLucesGenerales(); break;
                }
            });
            topPanel.add(btn);
        }




// — Panel de LEDs —
// JPanel ledsPanel = … (lo vamos a reemplazar)
        for (int i = 0; i < LED_COUNT; i++) {
            JLabel led = new JLabel();
            ledLabels[i] = led; // <— guarda la referencia
            led.setOpaque(true);
            led.setPreferredSize(new Dimension(20, 20));
            led.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
// inicialízalos con el estado de tu último fetch si quieres
        }

        // — Tabla con datos de telemetría —
        DefaultTableModel tableModel = new DefaultTableModel(TABLE_COLUMNS, 0);



        // … después de crear y rellenar el DefaultTableModel:
        final JTable table = new JTable(tableModel);
        JScrollPane tableScroll = new JScrollPane(table);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> {
            // 1) borra datos previos y re-fetch
            telemetryData.clear();
            initMongoDB();
            // 2) actualiza la tabla
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            model.setRowCount(0);
            updateIndicators(model);
            for (Document doc : telemetryData) {
                @SuppressWarnings("unchecked")
                List<Integer> datosList = doc.getList("datos", Integer.class);
                Object[] row = new Object[TABLE_COLUMNS.length];
                // msgCount en columna 0
                row[0] = doc.getInteger("msgCount");
                // datos seleccionados en columnas 1…5
                for (int j = 0; j < DATA_INDEXES.length; j++) {
                    row[j + 1] = datosList.get(DATA_INDEXES[j]);
                }
                model.addRow(row);
            }
            // 3) actualiza LEDs sólo con el último documento
            Document last = telemetryData.get(telemetryData.size() - 1);
            List<Integer> datos = last.getList("datos", Integer.class);
            for (int i = 0; i < LED_COUNT; i++) {
                ledLabels[i].setBackground(
                        datos.get(i) != 0 ? Color.GREEN : Color.RED
                );
            }
        });

        JPanel ledsAndRefresh = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        for (JLabel led : ledLabels) ledsAndRefresh.add(led);
        ledsAndRefresh.add(refreshBtn);

        // antes de northPanel.add(ledsAndRefresh…)
        totalCol2Label = new JLabel("Total C2: 0");
        totalCol3Label = new JLabel("Total C3: 0");
        totalCol4Label = new JLabel("Total C4: 0");

        // añade los indicadores junto a LEDs y Refresh
        ledsAndRefresh.add(Box.createHorizontalStrut(20)); // separación opcional
        ledsAndRefresh.add(totalCol2Label);
        ledsAndRefresh.add(totalCol3Label);
        ledsAndRefresh.add(totalCol4Label);

// finalmente reemplaza donde antes añadías `ledsPanel`
        northPanel.add(ledsAndRefresh, BorderLayout.SOUTH);





        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);
        for (Document doc : telemetryData) {
            @SuppressWarnings("unchecked")
            List<Integer> datosList = doc.getList("datos", Integer.class);
            Object[] row = new Object[TABLE_COLUMNS.length];
            // 1ª columna = msgCount
            row[0] = doc.getInteger("msgCount");
            // columnas 2–6 según DATA_INDEXES
            for (int j = 0; j < DATA_INDEXES.length; j++) {
                row[j + 1] = datosList.get(DATA_INDEXES[j]);
            }
            model.addRow(row);
        }



//        JTable table = new JTable(tableModel);
//        JScrollPane tableScroll = new JScrollPane(table);

        // — Panel inferior: toggle y descargar —
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JToggleButton toggle = new JToggleButton("Normal");
        toggle.addItemListener(e -> {
            boolean simulado = e.getStateChange() == ItemEvent.SELECTED;
            toggle.setText(simulado ? "Simulado" : "Normal");
            int factor = simulado ? 5 : 1;

            // índices en el array 'datos' que quieres multiplicar
            int[] dataIdx = {10, 11, 12};
            for (int row = 0; row < tableModel.getRowCount(); row++) {
                @SuppressWarnings("unchecked")
                List<Integer> datosList = telemetryData.get(row).getList("datos", Integer.class);
                for (int k = 0; k < dataIdx.length; k++) {
                    int original = datosList.get(dataIdx[k]);
                    // columnas 2,3,4 en la tabla
                    tableModel.setValueAt(original * factor, row, k + 2);
                }
            }
            updateIndicators(tableModel);
        });


        // Botón para borrar todos los datos de MongoDB
        JButton deleteBtn = new JButton("Borrar Datos");
        deleteBtn.addActionListener(e -> {
            // 1) Borrar en la colección
            try (MongoClient mongoClient = MongoClients.create(MONGO_CONN_STR)) {
                MongoDatabase db = mongoClient.getDatabase("lucesdb");
                MongoCollection<Document> coll = db.getCollection("telemetria");
                coll.deleteMany(new Document());  // borra todo
                System.out.println("[MongoDB] Todos los documentos eliminados.");
            } catch (Exception ex) {
                System.err.println("[MongoDB] Error al borrar: " + ex.getMessage());
            }
            // 2) Limpiar lista local y tabla
            telemetryData.clear();
            DefaultTableModel model2 = (DefaultTableModel) table.getModel();
            model2.setRowCount(0);
            updateIndicators(model2);
            // 3) Resetear LEDs a rojo
            for (int i = 0; i < LED_COUNT; i++) {
                ledLabels[i].setBackground(Color.RED);
            }
        });

        JButton downloadBtn = new JButton("Descargar");
        downloadBtn.addActionListener(e -> {
            System.out.println("Descargar presionado");
            // aquí podrías serializar telemetryData a un archivo, CSV, etc.
        });

        bottomPanel.add(toggle);
        bottomPanel.add(downloadBtn);

        // — Layout final —
        frame.setLayout(new BorderLayout());
        frame.add(northPanel, BorderLayout.NORTH);
        frame.add(tableScroll, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);
        frame.setVisible(true);
    }


    // 3) Función auxiliar para enviar a Azure:
    private static void sendPayload(int botonId, int[] datos) {
        if (serviceClient == null) return;
        new Thread(() -> {
            try {
                // Construye JSON
                StringBuilder sb = new StringBuilder();
                sb.append("{\"boton\":").append(botonId).append(",\"datos\":[");
                for (int j = 0; j < datos.length; j++) {
                    sb.append(datos[j]);
                    if (j < datos.length - 1) sb.append(",");
                }
                sb.append("]}");
                String payload = sb.toString();

                serviceClient.send(DEVICE_ID, new Message(payload));
                System.out.println("[C2D] Enviado desde botón " + botonId + ": " + payload);
            } catch (Exception ex) {
                System.err.println("[C2D] Error al enviar del botón " + botonId + ": " + ex.getMessage());
            }
        }).start();
    }

    // 4) Funciones por botón —en cada una personalizas tu arreglo de 15 datos:
    private static void handleReset() {
        int[] datos = new int[15];
        // ejemplo: todos a cero salvo la posición 0 a 1
        datos[0] = 1;
        // … personaliza más índices si lo necesitas …
        sendPayload(1, datos);
    }

    private static void handleEncender() {
        int[] datos = new int[15];
        // ejemplo: posición 1 a 1
        datos[1] = 1;
        // …
        sendPayload(2, datos);
    }

    private static void handleApagar() {
        int[] datos = new int[15];
        // ejemplo: posición 2 a 1
        datos[2] = 1;
        sendPayload(3, datos);
    }

    private static void handleActivarS1() {
        int[] datos = new int[15];
        // ejemplo: posición 3 a 1
        datos[3] = 1;
        sendPayload(4, datos);
    }

    private static void handleActivarS2() {
        int[] datos = new int[15];
        // ejemplo: posición 4 a 1
        datos[4] = 1;
        sendPayload(5, datos);
    }

    private static void handleApagarLucesGenerales() {
        int[] datos = new int[15];
        // ejemplo: posición 5 a 1
        datos[5] = 1;
        sendPayload(6, datos);
    }

    private static void updateIndicators(DefaultTableModel model) {
        int lastRow = model.getRowCount() - 1;
        if (lastRow >= 0) {
            Object v2 = model.getValueAt(lastRow, 2);
            Object v3 = model.getValueAt(lastRow, 3);
            Object v4 = model.getValueAt(lastRow, 4);
            totalCol2Label.setText("C2: " + v2);
            totalCol3Label.setText("C3: " + v3);
            totalCol4Label.setText("C4: " + v4);
        } else {
            totalCol2Label.setText("C2: —");
            totalCol3Label.setText("C3: —");
            totalCol4Label.setText("C4: —");
        }
    }
}
