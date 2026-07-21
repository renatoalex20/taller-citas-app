package edu.pe.cibertec.taller.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.pe.cibertec.taller.excepcion.HorarioNoPermitidoException;
import edu.pe.cibertec.taller.excepcion.HorarioOcupadoException;
import edu.pe.cibertec.taller.modelo.Cita;
import edu.pe.cibertec.taller.modelo.EstadoCita;
import edu.pe.cibertec.taller.modelo.Mecanico;
import edu.pe.cibertec.taller.modelo.ResultadoCancelacion;
import edu.pe.cibertec.taller.modelo.TipoServicio;
import edu.pe.cibertec.taller.repositorio.RepositorioCitas;
import edu.pe.cibertec.taller.repositorio.RepositorioMecanicos;
import edu.pe.cibertec.taller.servicio.impl.ServicioCitasImpl;
import edu.pe.cibertec.taller.util.ProveedorFechaHora;
import edu.pe.cibertec.taller.util.ServicioNotificaciones;

import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class GestionCitasSteps {

	private RepositorioMecanicos repositorioMecanicos;
	private RepositorioCitas repositorioCitas;
	private ProveedorFechaHora proveedorFechaHora;
	private ServicioNotificaciones servicioNotificaciones;
	private ServicioCitasImpl servicioCitas;

	private Cita citaResultado;
	private ResultadoCancelacion resultadoCancelacion;
	private Exception excepcionCapturada;
	private final LocalDate fechaHoy = LocalDate.of(2026, 9, 18);

	@Before
	public void inicializar() {
		repositorioMecanicos = mock(RepositorioMecanicos.class);
		repositorioCitas = mock(RepositorioCitas.class);
		proveedorFechaHora = mock(ProveedorFechaHora.class);
		servicioNotificaciones = mock(ServicioNotificaciones.class);
		servicioCitas = new ServicioCitasImpl(repositorioMecanicos, repositorioCitas,
				proveedorFechaHora, servicioNotificaciones);

		when(proveedorFechaHora.ahora()).thenReturn(fechaHoy.minusDays(1).atTime(8, 0));
		citaResultado = null;
		resultadoCancelacion = null;
		excepcionCapturada = null;
	}

	//PRIMER ESCENARIO
	@Given("un mecanico disponible con ID {long} para CAMBIO_ACEITE")
	public void unMecanicoDisponibleConIDParaCAMBIO_ACEITE(Long mecanicoId) {
		Mecanico mecanico = new Mecanico(mecanicoId, "Alex Velazco", TipoServicio.CAMBIO_ACEITE);
		when(repositorioMecanicos.findById(mecanicoId)).thenReturn(Optional.of(mecanico));
		when(repositorioCitas.findByMecanicoIdAndEstado(eq(mecanicoId), eq(EstadoCita.PROGRAMADA)))
				.thenReturn(Collections.emptyList());
	}

	@When("intento agendar un CAMBIO_ACEITE para la placa {string} con el mecanico {long} a las {int}:{int}")
	public void intentoAgendarUnCAMBIO_ACEITEParaLaPlacaConElMecanicoALas(String placa, Long mecanicoId, int hora, int minuto) {
		LocalDateTime fechaHoraInicio = fechaHoy.atTime(hora, minuto);
		when(proveedorFechaHora.ahora()).thenReturn(fechaHoy.minusDays(1).atTime(8, 0));
		when(repositorioCitas.save(any(Cita.class))).thenAnswer(invocation -> invocation.getArgument(0));

		try {
			citaResultado = servicioCitas.agendarCita(mecanicoId, placa, TipoServicio.CAMBIO_ACEITE, fechaHoraInicio);
		} catch (Exception e) {
			excepcionCapturada = e;
		}
	}

	@Then("la cita queda programada exitosamente")
	public void laCitaQuedaProgramadaExitosamente() {
		assertNull(excepcionCapturada);
		assertNotNull(citaResultado);
		assertEquals(EstadoCita.PROGRAMADA, citaResultado.getEstado());
	}

	@And("se notifica la cita agendada")
	public void seNotificaLaCitaAgendada() {
		verify(servicioNotificaciones, times(1)).notificarCitaAgendada(any(Cita.class));
	}

	//SEGUNDO ESCENARIO
	@Given("un mecanico disponible con ID {long} para REPARACION_MOTOR")
	public void unMecanicoDisponibleConIDParaREPARACION_MOTOR(Long mecanicoId) {
		Mecanico mecanico = new Mecanico(mecanicoId, "Alex Velazco", TipoServicio.REPARACION_MOTOR);
		when(repositorioMecanicos.findById(mecanicoId)).thenReturn(Optional.of(mecanico));
	}

	@When("intento agendar una REPARACION_MOTOR para la placa {string} con el mecanico {long} a las {int}:{int}")
	public void intentoAgendarUnaREPARACION_MOTORParaLaPlacaConElMecanicoALas(String placa, Long mecanicoId, int hora, int minuto) {
		LocalDateTime fechaHoraInicio = fechaHoy.atTime(hora, minuto);
		try {
			citaResultado = servicioCitas.agendarCita(mecanicoId, placa, TipoServicio.REPARACION_MOTOR, fechaHoraInicio);
		} catch (Exception e) {
			excepcionCapturada = e;
		}
	}

	@Then("el sistema rechaza la cita por horario no permitido")
	public void elSistemaRechazaLaCitaPorHorarioNoPermitido() {
		assertNotNull(excepcionCapturada);
		assertTrue(excepcionCapturada instanceof HorarioNoPermitidoException);
	}

	//TERCER ESCENARIO
	@Given("una cita programada con ID {int} para las {int}:{int}")
	public void unaCitaProgramadaConIDParaLas(Integer citaId, Integer hora, Integer minuto) {
		LocalDateTime fechaHoraCita = fechaHoy.atTime(hora, minuto);
		Mecanico mecanico = new Mecanico(1L, "Alex Velazco", TipoServicio.CAMBIO_ACEITE);
		Cita cita = new Cita(citaId.longValue(), mecanico, "VEL-028", TipoServicio.CAMBIO_ACEITE, fechaHoraCita, 1, EstadoCita.PROGRAMADA);

		when(repositorioCitas.findById(citaId.longValue())).thenReturn(Optional.of(cita));
	}

	@When("intento cancelar la cita {long} con {int} horas de anticipacion")
	public void intentoCancelarLaCitaConHorasDeAnticipacion(Long citaId, int horasAnticipacion) {
		LocalDateTime fechaHoraCita = fechaHoy.atTime(10, 0);
		when(proveedorFechaHora.ahora()).thenReturn(fechaHoraCita.minusHours(horasAnticipacion));

		try {
			resultadoCancelacion = servicioCitas.cancelarCita(citaId);
		} catch (Exception e) {
			excepcionCapturada = e;
		}
	}

	@Then("la cita se cancela con una penalidad de {double}")
	public void laCitaSeCancelaConUnaPenalidadDe(Double penalidadEsperada) {
		assertNull(excepcionCapturada);
		assertNotNull(resultadoCancelacion);
		assertEquals(penalidadEsperada, resultadoCancelacion.getMontoPenalidad(), 0.001);
	}

	// CUARTO ESCENARIO
	@Given("un mecanico con ID {long} que tiene una cita programada de 10:00 a 11:00")
	public void unMecanicoConIDQueTieneUnaCitaProgramadaDe10a11(Long mecanicoId) {
		Mecanico mecanico = new Mecanico(mecanicoId, "Alex Velazco", TipoServicio.CAMBIO_ACEITE);
		when(repositorioMecanicos.findById(mecanicoId)).thenReturn(Optional.of(mecanico));

		LocalDateTime inicioExistente = fechaHoy.atTime(10, 0);
		Cita citaExistente = new Cita(100L, mecanico, "ABC-123", TipoServicio.CAMBIO_ACEITE, inicioExistente, 1, EstadoCita.PROGRAMADA);

		when(repositorioCitas.findByMecanicoIdAndEstado(eq(mecanicoId), any()))
				.thenReturn(List.of(citaExistente));
	}

	@Then("el sistema rechaza la cita por horario ocupado")
	public void elSistemaRechazaLaCitaPorHorarioOcupado() {
		assertNotNull(excepcionCapturada);
		assertTrue(excepcionCapturada instanceof HorarioOcupadoException);
	}
}