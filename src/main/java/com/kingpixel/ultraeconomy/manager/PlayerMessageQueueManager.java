package com.kingpixel.ultraeconomy.manager;

import com.kingpixel.ultraeconomy.UltraEconomy;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author Carlos Varas Alonso - 11/10/2025 7:51
 */
public class PlayerMessageQueueManager {

  private static final ConcurrentHashMap<UUID, Queue<Runnable>> MESSAGE_QUEUES = new ConcurrentHashMap<>();

  /**
   * Initialize the message queue scheduler.
   * Must be called after config is loaded (SERVER_STARTED).
   */
  public static void init() {
    long delayMs = UltraEconomy.config.getBetweenMessagesDelay().toMillis();
    UltraEconomy.getAsyncContext().scheduleAtFixedRate(
      PlayerMessageQueueManager::processQueues,
      0, delayMs, TimeUnit.MILLISECONDS
    );
  }

  /**
   * Añadir un mensaje a la cola de un jugador
   */
  public static void enqueue(UUID playerUUID, Runnable messageAction) {
    MESSAGE_QUEUES.computeIfAbsent(playerUUID, id -> new ConcurrentLinkedQueue<>()).add(messageAction);
  }

  /**
   * Procesa una pila por jugador (envía un mensaje por ciclo)
   */
  private static void processQueues() {
    for (var entry : MESSAGE_QUEUES.entrySet()) {
      UUID playerUUID = entry.getKey();
      Queue<Runnable> queue = entry.getValue();

      Runnable action = queue.poll();
      if (action != null) {
        try {
          action.run();
        } catch (Exception e) {
          if (UltraEconomy.config.isDebug()) {
            UltraEconomy.LOGGER.error("Error sending queued message for player " + playerUUID, e);
          }
        }
      }
      if (queue.isEmpty()) {
        MESSAGE_QUEUES.remove(playerUUID);
      }
    }
  }
}
