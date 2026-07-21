package edu.pe.cibertec.taller.servicio;

import edu.pe.cibertec.taller.excepcion.EspecialidadIncorrectaException;
import edu.pe.cibertec.taller.excepcion.FechaInvalidaException;
import edu.pe.cibertec.taller.excepcion.HorarioNoPermitidoException;
import edu.pe.cibertec.taller.excepcion.MecanicoNoEncontradoException;
import edu.pe.cibertec.taller.modelo.Cita;
import edu.pe.cibertec.taller.modelo.EstadoCita;
import edu.pe.cibertec.taller.modelo.Mecanico;
import edu.pe.cibertec.taller.modelo.TipoServicio;
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
		// TODO

		// Act y Assert
		// TODO
	}

	@Test
	@DisplayName("Una cita que empieza justo cuando termina otra se acepta")
	void agendarCitaContigua() {
		// Arrange
		// TODO: una cita existente que termina a las 10:00 y la nueva que empieza a las 10:00

		// Act
		// TODO

		// Assert
		// TODO
	}

	@Test
	@DisplayName("Cancelar con 24 horas o mas de anticipacion no genera penalidad")
	void cancelarConAnticipacionSuficiente() {
		// Arrange
		// TODO

		// Act
		// TODO

		// Assert
		// TODO: penalidad 0, estado CANCELADA, notificacion
	}

	@Test
	@DisplayName("Cancelar con menos de 24 horas aplica una penalidad de 50.00")
	void cancelarConAvisoTardio() {
		// Arrange
		// TODO

		// Act
		// TODO

		// Assert
		// TODO
	}

	@Test
	@DisplayName("Cancelar una cita inexistente lanza CitaNoEncontradaException")
	void cancelarCitaInexistente() {
		// Arrange
		// TODO

		// Act y Assert
		// TODO
	}

	@Test
	@DisplayName("Cancelar una cita que ya fue cancelada lanza CitaNoCancelableException")
	void cancelarCitaYaCancelada() {
		// Arrange
		// TODO

		// Act y Assert
		// TODO
	}

	@Test
	@DisplayName("Buscar mecanico disponible retorna el primero sin citas superpuestas")
	void buscarMecanicoDisponibleRetornaPrimeroLibre() {
		// Arrange
		// TODO: dos mecanicos de la misma especialidad, el primero ocupado

		// Act
		// TODO

		// Assert
		// TODO
	}

	@Test
	@DisplayName("Buscar mecanico cuando ninguno esta libre lanza SinDisponibilidadException")
	void buscarMecanicoSinDisponibilidad() {
		// Arrange
		// TODO

		// Act y Assert
		// TODO
	}
}
