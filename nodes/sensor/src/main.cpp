#include <Arduino.h>
#include <painlessMesh.h>
#include <MqttMesh.h>

#include "config.h"

Scheduler userScheduler;
MqttMesh mesh;

unsigned long timeStart;
unsigned long initiateDeepSleepTime;

bool foundBridge = false;

void deepSleep();

void initiateDeepSleep();

String topicPath() {
    return String("devices/") + DEVICE_TYPE + "/" + mesh.getNodeId();
}

int readSensorValue() {
    return analogRead(SENSOR_PIN);
}

void turnSensorOff() {
    Serial.printf("MEASUREMENT - Turn off Sensor %lu\n", millis());
    digitalWrite(SENSOR_POWER_PIN, LOW);
}

int readAndNormalizeSensorValue() {
    Serial.printf("MEASUREMENT - Start ----- %lu\n", millis());

    double sum = 0.0;
    for (int i = 0; i < MEASUREMENTS; i++) {
        auto value = (double) readSensorValue();
        Serial.printf("MEASUREMENT - %d Value %f\n",i,value);
        sum += value;
    }

    double average = sum / MEASUREMENTS;
    long percent = map((long) average, SENSORS_MIN, SENSORS_MAX, 100, 0);

    Serial.printf("MEASUREMENT - Percent %ld\n",percent);
    Serial.println("MEASUREMENT - End -----");

    turnSensorOff();

    return (int) percent;
}

void measureMoistureAndSend() {
    DynamicJsonDocument jsonDocument(128);
    jsonDocument["moisture"] = readAndNormalizeSensorValue();

    String statePayload;
    serializeJson(jsonDocument, statePayload);

    Serial.println("MESH -> MQTT: " + statePayload);

    mesh.sendMqtt(topicPath() + "/state", statePayload);
}

void sleep() {
    mesh.stop();
    ESP.deepSleep(SLEEP_TIME_SEC * 1000000, WAKE_RF_DEFAULT);
}

void introduceSensor() {
    Serial.println("MESH -> BRIDGE: Publishing node, got notification");

    DynamicJsonDocument configJson(512);

    configJson["name"] = HR_NAME;
    configJson["unique_id"] = String(mesh.getNodeId());
    configJson["device_class"] = "humidity";
    configJson["stat_t"] = topicPath() + "/state";
    configJson["unit_of_measurement"] = "%";
    configJson["frc_upd"] = true;
    configJson["value_template"] = "{{ value_json.moisture}}";

    String configPayload;
    serializeJson(configJson, configPayload);

    mesh.sendMqtt(topicPath() + "/config", configPayload);

    Serial.println("MESH -> BRIDGE: Config message: " + configPayload);
}

void bridgeAvailableCallback() {
    foundBridge = true;

    introduceSensor();
    measureMoistureAndSend();

    initiateDeepSleep();
}

void initiateDeepSleep() {
    initiateDeepSleepTime = millis();
}

void setup() {
    Serial.begin(115200);
    pinMode(SENSOR_POWER_PIN, OUTPUT);
    digitalWrite(SENSOR_POWER_PIN, HIGH);

    mesh.setDebugMsgTypes(ERROR | STARTUP | CONNECTION);
    mesh.init(MESH_PREFIX, MESH_PASSWORD, &userScheduler, MESH_PORT, WIFI_STA, CHANNEL);
    mesh.setContainsRoot(true);

    mesh.onBridgeAvailable(&bridgeAvailableCallback);

    timeStart = millis();
}

void loop() {
    mesh.update();

    bool timout = millis() - timeStart > TIMEOUT_SEC * 1000;
    bool waitedForSendMesh = millis() - initiateDeepSleepTime > 100;
    if ((!foundBridge && timout) || (foundBridge && waitedForSendMesh)) {
        deepSleep();
    }

}

void deepSleep() {
    Serial.println("DEEP_SLEEP: Enable");
    Serial.printf("DEEP_SLEEP: Was awake for %lu milliseconds \n", millis() - timeStart);

    mesh.stop();
    mesh.update();

    WiFi.mode(WIFI_OFF);
    WiFi.forceSleepBegin();
    ESP.deepSleep(SLEEP_TIME_SEC * 1000 * 1000);
}
