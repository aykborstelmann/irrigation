package org.irrigiation.sensor.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;

@Configuration
public class MqttConfig {

    @Bean
    public IntegrationFlow mqttInbound(MqttPahoMessageDrivenChannelAdapter mqttPahoMessageDrivenChannelAdapter) {
        return IntegrationFlows.from(mqttPahoMessageDrivenChannelAdapter)
            .handle(m -> System.out.println(m.getPayload()))
            .get();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttPahoMessageDrivenChannelAdapter(@Value("tcp://${mqtt.host}:${mqtt.port}") String url) {
        return new MqttPahoMessageDrivenChannelAdapter(url, "spring", "#");
    }

}
