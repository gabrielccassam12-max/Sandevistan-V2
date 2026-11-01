package com.sandevistan.client;

import com.sandevistan.Phase;

/** Estado do Sandevistan do jogador local (a contagem é só para a UI; o servidor é quem manda). */
public final class ClientState {
    public static Phase phase = Phase.READY;
    public static int remaining;
    public static int total;
    /** Ticks desde a ativação (anima a sequência "implante ligando"). */
    public static int sinceActivation = 9999;

    private ClientState() {}

    public static void update(Phase p, int rem, int tot) {
        if (p == Phase.ACTIVE && phase != Phase.ACTIVE) sinceActivation = 0;
        phase = p;
        remaining = rem;
        total = tot;
    }

    public static void tick() {
        if (remaining > 0 && phase != Phase.READY) remaining--;
        if (sinceActivation < 9999) sinceActivation++;
    }

    public static void reset() {
        phase = Phase.READY;
        remaining = total = 0;
        sinceActivation = 9999;
    }
}
