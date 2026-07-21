package edu.pe.cibertec.taller.servicio;

import edu.pe.cibertec.taller.excepcion.*;
import edu.pe.cibertec.taller.modelo.*;
import edu.pe.cibertec.taller.repositorio.RepositorioCitas;
import edu.pe.cibertec.taller.repositorio.RepositorioMecanicos;
import edu.pe.cibertec.taller.servicio.impl.ServicioCitasImpl;
import edu.pe.cibertec.taller.util.ProveedorFechaHora;
import edu.pe.cibertec.taller.util.ServicioNotificaciones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServicioCitasImplTest {

	@Mock
	private RepositorioMecanicos repositorioMecanicos;

	@Mock
	private RepositorioCitas repositorioCitas;

	@Mock
	private ProveedorFechaHora proveedorFechaHora;

	@Mock
	private ServicioNotificaciones servicioNotificaciones;

	private ServicioCitasImpl servicioCitas;

	@BeforeEach
	void inicializar() {
		servicioCitas = new ServicioCitasImpl(repositorioMecanicos, repositorioCitas,
				proveedorFechaHora, servicioNotificaciones);
		// TODO: crear aqui los datos comunes que necesiten los tests
	}

	@Test
	@DisplayName("Agendar una cita valida la guarda, notifica y la retorna en estado PROGRAMADA")
	void agendarCitaExitosa() {
		// Arrange
		LocalDate fechaCita = LocalDate.of(2026, 9, 18);
		LocalDateTime fechaHoraInicio = fechaCita.atTime(10, 0);

		LocalDateTime relojSimulador = fechaCita.minusDays(1).atTime(8, 0);
		when(proveedorFechaHora.ahora()).thenReturn(relojSimulador);

		Mecanico mecanico = new Mecanico(1L, "Alex Velazco", TipoServicio.CAMBIO_ACEITE);
		when(repositorioMecanicos.findById(1L)).thenReturn(Optional.of(mecanico));
		when(repositorioCitas.findByMecanicoIdAndEstado(eq(1L), any())).thenReturn(Collections.emptyList());
		when(repositorioCitas.save(any(Cita.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		Cita citaCreada = servicioCitas.agendarCita(1L, "VEL-028", TipoServicio.CAMBIO_ACEITE, fechaHoraInicio);

		// Assert
		assertEquals(EstadoCita.PROGRAMADA, citaCreada.getEstado());
		assertEquals(TipoServicio.CAMBIO_ACEITE.getDuracionHoras(), citaCreada.getDuracionHoras());
		verify(repositorioCitas, times(1)).save(any(Cita.class));
		verify(servicioNotificaciones, times(1)).notificarCitaAgendada(any(Cita.class));
	}

	@Test
	@DisplayName("Agendar con un mecanico inexistente lanza MecanicoNoEncontradoException")
	void agendarConMecanicoInexistente() {
		// Arrange
		Long idMecanicoInexistente = 99L;
		LocalDate fechaCita = LocalDate.of(2026, 9, 18);
		LocalDateTime fechaHoraInicio = fechaCita.atTime(10, 0);

		when(repositorioMecanicos.findById(idMecanicoInexistente)).thenReturn(Optional.empty());

		// Act y Assert
		assertThrows(MecanicoNoEncontradoException.class, () -> {
			servicioCitas.agendarCita(idMecanicoInexistente, "VEL-028", TipoServicio.CAMBIO_ACEITE, fechaHoraInicio);
		});
		verify(repositorioCitas, never()).save(any());
	}

	@Test
	@DisplayName("Agendar cuando la especialidad no coincide lanza EspecialidadIncorrectaException")
	void agendarConEspecialidadIncorrecta() {
		// Arrange
		LocalDate fechaCita = LocalDate.of(2026, 9, 18);
		LocalDateTime fechaHoraInicio = fechaCita.atTime(10, 0);
		Mecanico mecanico = new Mecanico(1L, "Alex Velazco", TipoServicio.CAMBIO_ACEITE);
		when(repositorioMecanicos.findById(1L)).thenReturn(Optional.of(mecanico));

		// Act y Assert
		assertThrows(EspecialidadIncorrectaException.class, () -> {
			servicioCitas.agendarCita(1L, "VEL-028", TipoServicio.REPARACION_MOTOR, fechaHoraInicio);
		});
		verify(repositorioCitas, never()).save(any());
	}

	@Test
	@DisplayName("Un servicio pesado a las 15:00 se rechaza con HorarioNoPermitidoException")
	void agendarServicioPesadoEnLaTarde() {
		// Arrange
		LocalDate fechaCita = LocalDate.of(2026, 9, 18);
		LocalDateTime fechaHoraInicio = fechaCita.atTime(15, 0);

		Mecanico mecanico = new Mecanico(1L, "Alex Velazco", TipoServicio.REPARACION_MOTOR);
		when(repositorioMecanicos.findById(1L)).thenReturn(Optional.of(mecanico));

		// Act y Assert
		assertThrows(HorarioNoPermitidoException.class, () -> {
			servicioCitas.agendarCita(1L, "VEL-028", TipoServicio.REPARACION_MOTOR, fechaHoraInicio);
		});
		verify(repositorioCitas, never()).save(any());
	}

	@Test
	@DisplayName("Un servicio pesado a las 09:00 se acepta y se guarda")
	void agendarServicioPesadoEnLaManana() {
		// Arrange
		LocalDate fechaCita = LocalDate.of(2026, 9, 18);
		LocalDateTime fechaHoraInicio = fechaCita.atTime(9, 0);

		LocalDateTime relojSimulado = fechaCita.minusDays(1).atTime(8, 0);
		when(proveedorFechaHora.ahora()).thenReturn(relojSimulado);

		Mecanico mecanico = new Mecanico(1L, "Alex Velazco", TipoServicio.REPARACION_MOTOR);
		when(repositorioMecanicos.findById(1L)).thenReturn(Optional.of(mecanico));
		when(repositorioCitas.findByMecanicoIdAndEstado(eq(1L), any())).thenReturn(Collections.emptyList());
		when(repositorioCitas.save(any(Cita.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		Cita citaCreada = servicioCitas.agendarCita(1L, "VEL-028", TipoServicio.REPARACION_MOTOR, fechaHoraInicio);

		// Assert
		assertEquals(EstadoCita.PROGRAMADA, citaCreada.getEstado());
		verify(repositorioCitas, times(1)).save(any(Cita.class));
		verify(servicioNotificaciones, times(1)).notificarCitaAgendada(any(Cita.class));
	}

	@Test
	@DisplayName("Agendar en una fecha del pasado lanza FechaInvalidaException")
	void agendarConFechaEnElPasado() {
		// Arrange
		LocalDateTime ahora = LocalDateTime.of(2026, 9, 18, 10, 0);
		when(proveedorFechaHora.ahora()).thenReturn(ahora);

		LocalDateTime fechaEnElPasado = ahora.minusHours(2);
		Mecanico mecanico = new Mecanico(1L, "Alex Velazco", TipoServicio.CAMBIO_ACEITE);
		when(repositorioMecanicos.findById(1L)).thenReturn(Optional.of(mecanico));

		// Act y Assert
		assertThrows(FechaInvalidaException.class, () -> {
			servicioCitas.agendarCita(1L, "VEL-028", TipoServicio.CAMBIO_ACEITE, fechaEnElPasado);
		});
		verify(repositorioCitas, never()).save(any());
	}

	@Test
	@DisplayName("Agendar sobre una cita ya programada se rechaza con HorarioOcupadoException")
	void agendarConSuperposicion() {
		// Arrange
		LocalDate fechaCita = LocalDate.of(2026, 9, 18);
		LocalDateTime fechaHoraInicio = fechaCita.atTime(10, 0);

		LocalDateTime relojSimulado = fechaCita.minusDays(1).atTime(8, 0);
		when(proveedorFechaHora.ahora()).thenReturn(relojSimulado);

		Mecanico mecanico = new Mecanico(1L, "Alex Velazco", TipoServicio.CAMBIO_ACEITE);
		when(repositorioMecanicos.findById(1L)).thenReturn(Optional.of(mecanico));

		Cita citaExistente = new Cita(10L, mecanico, "ABC-123", TipoServicio.CAMBIO_ACEITE, fechaHoraInicio, 1, EstadoCita.PROGRAMADA);
		when(repositorioCitas.findByMecanicoIdAndEstado(eq(1L), eq(EstadoCita.PROGRAMADA)))
				.thenReturn(List.of(citaExistente));

		// Act y Assert
		assertThrows(HorarioOcupadoException.class, () -> {
			servicioCitas.agendarCita(1L, "VEL-028", TipoServicio.CAMBIO_ACEITE, fechaHoraInicio);
		});
		verify(repositorioCitas, never()).save(any());
	}

	@Test
	@DisplayName("Una cita que empieza justo cuando termina otra se acepta")
	void agendarCitaContigua() {
		// Arrange
		LocalDate fechaCita = LocalDate.of(2026, 9, 18);
		LocalDateTime inicioCitaExistente = fechaCita.atTime(9, 0);
		LocalDateTime inicioNuevaCita = fechaCita.atTime(10, 0);

		LocalDateTime relojSimulado = fechaCita.minusDays(1).atTime(8, 0);
		when(proveedorFechaHora.ahora()).thenReturn(relojSimulado);

		Mecanico mecanico = new Mecanico(1L, "Alex Velazco", TipoServicio.CAMBIO_ACEITE);
		when(repositorioMecanicos.findById(1L)).thenReturn(Optional.of(mecanico));

		Cita citaExistente = new Cita(10L, mecanico, "ABC-123", TipoServicio.CAMBIO_ACEITE, inicioCitaExistente, 1, EstadoCita.PROGRAMADA);
		when(repositorioCitas.findByMecanicoIdAndEstado(eq(1L), eq(EstadoCita.PROGRAMADA)))
				.thenReturn(List.of(citaExistente));
		when(repositorioCitas.save(any(Cita.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		Cita citaCreada = servicioCitas.agendarCita(1L, "VEL-028", TipoServicio.CAMBIO_ACEITE, inicioNuevaCita);

		// Assert
		assertEquals(EstadoCita.PROGRAMADA, citaCreada.getEstado());
		verify(repositorioCitas, times(1)).save(any(Cita.class));
		verify(servicioNotificaciones, times(1)).notificarCitaAgendada(any(Cita.class));
	}

	@Test
	@DisplayName("Cancelar con 24 horas o mas de anticipacion no genera penalidad")
	void cancelarConAnticipacionSuficiente() {
		// Arrange
		LocalDateTime fechaHoraCita = LocalDateTime.of(2026, 9, 18, 10, 0);
		LocalDateTime momentoCancelacion = fechaHoraCita.minusHours(24);
		when(proveedorFechaHora.ahora()).thenReturn(momentoCancelacion);

		Mecanico mecanico = new Mecanico(1L, "Alex Velazco", TipoServicio.CAMBIO_ACEITE);
		Cita cita = new Cita(10L, mecanico, "VEL-028", TipoServicio.CAMBIO_ACEITE, fechaHoraCita, 1, EstadoCita.PROGRAMADA);
		when(repositorioCitas.findById(10L)).thenReturn(Optional.of(cita));

		// Act
		ResultadoCancelacion resultado = servicioCitas.cancelarCita(10L);

		// Assert
		assertEquals(0.00, resultado.getMontoPenalidad(), 0.001);
		assertEquals(EstadoCita.CANCELADA, cita.getEstado());
		verify(servicioNotificaciones, times(1)).notificarCitaCancelada(cita);
	}

	@Test
	@DisplayName("Cancelar con menos de 24 horas aplica una penalidad de 50.00")
	void cancelarConAvisoTardio() {
		// Arrange
		LocalDateTime fechaHoraCita = LocalDateTime.of(2026, 9, 18, 10, 0);
		LocalDateTime momentoCancelacion = fechaHoraCita.minusHours(2);
		when(proveedorFechaHora.ahora()).thenReturn(momentoCancelacion);

		Mecanico mecanico = new Mecanico(1L, "Alex Velazco", TipoServicio.CAMBIO_ACEITE);
		Cita cita = new Cita(10L, mecanico, "VEL-028", TipoServicio.CAMBIO_ACEITE, fechaHoraCita, 1, EstadoCita.PROGRAMADA);
		when(repositorioCitas.findById(10L)).thenReturn(Optional.of(cita));

		// Act
		ResultadoCancelacion resultado = servicioCitas.cancelarCita(10L);

		// Assert
		assertEquals(50.00, resultado.getMontoPenalidad(), 0.001);
		assertEquals(EstadoCita.CANCELADA, cita.getEstado());
	}

	@Test
	@DisplayName("Cancelar una cita inexistente lanza CitaNoEncontradaException")
	void cancelarCitaInexistente() {
		// Arrange
		Long idInexistente = 99L;
		when(repositorioCitas.findById(idInexistente)).thenReturn(Optional.empty());

		// Act y Assert
		assertThrows(CitaNoEncontradaException.class, () -> {
			servicioCitas.cancelarCita(idInexistente);
		});
	}

	@Test
	@DisplayName("Cancelar una cita que ya fue cancelada lanza CitaNoCancelableException")
	void cancelarCitaYaCancelada() {
		// Arrange
		LocalDateTime fechaHoraCita = LocalDateTime.of(2026, 9, 18, 10, 0);
		Mecanico mecanico = new Mecanico(1L, "Alex Velazco", TipoServicio.CAMBIO_ACEITE);

		Cita cita = new Cita(10L, mecanico, "VEL-028", TipoServicio.CAMBIO_ACEITE, fechaHoraCita, 1, EstadoCita.ATENDIDA);
		when(repositorioCitas.findById(10L)).thenReturn(Optional.of(cita));

		// Act y Assert
		assertThrows(CitaNoCancelableException.class, () -> {
			servicioCitas.cancelarCita(10L);
		});
	}

	@Test
	@DisplayName("Buscar mecanico disponible retorna el primero sin citas superpuestas")
	void buscarMecanicoDisponibleRetornaPrimeroLibre() {
		// Arrange
		LocalDate fechaCita = LocalDate.of(2026, 9, 18);
		LocalDateTime fechaHoraInicio = fechaCita.atTime(10, 0);

		Mecanico mecanicoOcupado = new Mecanico(1L, "Alex Velazco", TipoServicio.CAMBIO_ACEITE);
		Mecanico mecanicoLibre = new Mecanico(2L, "Juan Perez", TipoServicio.CAMBIO_ACEITE);

		when(repositorioMecanicos.findByEspecialidad(TipoServicio.CAMBIO_ACEITE))
				.thenReturn(List.of(mecanicoOcupado, mecanicoLibre));

		Cita citaExistente = new Cita(10L, mecanicoOcupado, "ABC-123", TipoServicio.CAMBIO_ACEITE, fechaHoraInicio, 1, EstadoCita.PROGRAMADA);
		when(repositorioCitas.findByMecanicoIdAndEstado(eq(1L), eq(EstadoCita.PROGRAMADA)))
				.thenReturn(List.of(citaExistente));
		when(repositorioCitas.findByMecanicoIdAndEstado(eq(2L), eq(EstadoCita.PROGRAMADA)))
				.thenReturn(Collections.emptyList());

		// Act
		Mecanico resultado = servicioCitas.buscarMecanicoDisponible(TipoServicio.CAMBIO_ACEITE, fechaHoraInicio);

		// Assert
		assertEquals(2L, resultado.getId());
	}

	@Test
	@DisplayName("Buscar mecanico cuando ninguno esta libre lanza SinDisponibilidadException")
	void buscarMecanicoSinDisponibilidad() {
		// Arrange
		LocalDate fechaCita = LocalDate.of(2026, 9, 18);
		LocalDateTime fechaHoraInicio = fechaCita.atTime(10, 0);

		Mecanico mecanicoOcupado = new Mecanico(1L, "Alex Velazco", TipoServicio.CAMBIO_ACEITE);

		when(repositorioMecanicos.findByEspecialidad(TipoServicio.CAMBIO_ACEITE))
				.thenReturn(List.of(mecanicoOcupado));

		Cita citaExistente = new Cita(10L, mecanicoOcupado, "ABC-123", TipoServicio.CAMBIO_ACEITE, fechaHoraInicio, 1, EstadoCita.PROGRAMADA);
		when(repositorioCitas.findByMecanicoIdAndEstado(eq(1L), eq(EstadoCita.PROGRAMADA)))
				.thenReturn(List.of(citaExistente));

		// Act y Assert
		assertThrows(SinDisponibilidadException.class, () -> {
			servicioCitas.buscarMecanicoDisponible(TipoServicio.CAMBIO_ACEITE, fechaHoraInicio);
		});
	}
}
