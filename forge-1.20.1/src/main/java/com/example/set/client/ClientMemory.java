package com.example.set.client;

import java.util.LinkedHashMap;
import java.util.Map;

/** Recuerda (mientras el juego este abierto) el ultimo modo y los encantamientos elegidos en la pantalla de transferencia. */
final class ClientMemory {
    static boolean copyMode = false;
    static Map<String, Boolean> selection = new LinkedHashMap<>(); // id -> true = copiar, false = mover

    private ClientMemory() {}
}
