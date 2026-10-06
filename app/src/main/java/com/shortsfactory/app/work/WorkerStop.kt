package com.shortsfactory.app.work

import androidx.work.ListenableWorker
import androidx.work.WorkInfo

/**
 * `true` quando o worker foi parado porque o APP cancelou o trabalho (`cancelUniqueWork`, `REPLACE`),
 * ou seja, cancelamento do usuário. Qualquer outra parada (restrição não atendida, preempção, cota,
 * tempo limite) é do sistema: o WorkManager reagenda o trabalho e o estado gravado deve ser "na fila",
 * nunca "cancelado".
 */
internal fun ListenableWorker.cancelledByApp(): Boolean =
    stopReason == WorkInfo.STOP_REASON_CANCELLED_BY_APP
