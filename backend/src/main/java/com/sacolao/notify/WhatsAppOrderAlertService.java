package com.sacolao.notify;

import com.sacolao.establishment.entity.Establishment;
import com.sacolao.order.entity.FulfillmentType;
import com.sacolao.order.entity.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Service
public class WhatsAppOrderAlertService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppOrderAlertService.class);
    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));

    private final CallMeBotClient callMeBotClient;
    private final TelegramClient telegramClient;

    public WhatsAppOrderAlertService(CallMeBotClient callMeBotClient, TelegramClient telegramClient) {
        this.callMeBotClient = callMeBotClient;
        this.telegramClient = telegramClient;
    }

    public void notifyNewOrder(Establishment store, Order order) {
        if (store == null || order == null) {
            return;
        }
        String message = buildMessage(order);
        runAfterCommit(() -> {
            sendWhatsApp(store, message, order.getPublicCode());
            sendTelegram(store, message, order.getPublicCode());
        });
    }

    public boolean sendTelegramTest(Establishment store) {
        if (store == null || !store.isTelegramAlertEnabled()) {
            return false;
        }
        String storeName = store.getName() == null || store.getName().isBlank() ? "sua loja" : store.getName().trim();
        return telegramClient.sendMessage(
                store.getTelegramBotToken(),
                store.getTelegramChatId(),
                "MaxPedidos · Teste de alerta\nSe esta mensagem chegou, os pedidos novos da " + storeName + " avisam aqui."
        );
    }

    private void sendWhatsApp(Establishment store, String message, String code) {
        if (!store.isWhatsappAlertEnabled()) {
            return;
        }
        String phone = store.getWhatsappAlertPhone();
        String apiKey = store.getWhatsappAlertApikey();
        if (phone == null || phone.isBlank() || apiKey == null || apiKey.isBlank()) {
            return;
        }
        try {
            callMeBotClient.sendWhatsApp(phone, apiKey, message);
        } catch (Exception ex) {
            log.warn("Alerta WhatsApp não enviado order={}", code, ex);
        }
    }

    private void sendTelegram(Establishment store, String message, String code) {
        if (!store.isTelegramAlertEnabled()) {
            return;
        }
        try {
            telegramClient.sendMessage(store.getTelegramBotToken(), store.getTelegramChatId(), message);
        } catch (Exception ex) {
            log.warn("Alerta Telegram não enviado order={}", code, ex);
        }
    }

    private static void runAfterCommit(Runnable send) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    Thread.startVirtualThread(send);
                }
            });
        } else {
            Thread.startVirtualThread(send);
        }
    }

    private static String buildMessage(Order order) {
        String fulfillment = order.getFulfillmentType() == FulfillmentType.PICKUP ? "Retirada" : "Entrega";
        BigDecimal total = order.getTotal() == null ? BigDecimal.ZERO : order.getTotal();
        StringBuilder sb = new StringBuilder();
        sb.append("MaxPedidos · Novo pedido ").append(order.getPublicCode()).append('\n');
        sb.append(nullToDash(order.getCustomerName())).append('\n');
        sb.append(fulfillment).append(" · ").append(BRL.format(total)).append('\n');
        sb.append("Tel: ").append(nullToDash(order.getCustomerPhone()));
        if (order.getScheduledFor() != null) {
            sb.append("\nAgendado");
        }
        return sb.toString();
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }
}
