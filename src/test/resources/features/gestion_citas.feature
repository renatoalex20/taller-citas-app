Feature: Gestion de citas del taller mecanico

  Scenario: Agendar un cambio de aceite de forma exitosa
    Given un mecanico disponible con ID 1 para CAMBIO_ACEITE
    When intento agendar un CAMBIO_ACEITE para la placa "VEL-028" con el mecanico 1 a las 10:00
    Then la cita queda programada exitosamente
    And se notifica la cita agendada

  Scenario: Rechazar una reparacion de motor en la tarde
    Given un mecanico disponible con ID 1 para REPARACION_MOTOR
    When intento agendar una REPARACION_MOTOR para la placa "VEL-028" con el mecanico 1 a las 15:00
    Then el sistema rechaza la cita por horario no permitido

  Scenario: Cancelar con penalidad por aviso tardio
    Given una cita programada con ID 10 para las 10:00
    When intento cancelar la cita 10 con 2 horas de anticipacion
    Then la cita se cancela con una penalidad de 50.00

  Scenario: Rechazar un agendamiento por horario ocupado
    Given un mecanico con ID 1 que tiene una cita programada de 10:00 a 11:00
    When intento agendar un CAMBIO_ACEITE para la placa "VEL-028" con el mecanico 1 a las 10:00
    Then el sistema rechaza la cita por horario ocupado