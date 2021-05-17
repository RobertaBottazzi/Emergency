package model;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import model.Event.EventType;
import model.Patient.ColorCode;

public class Simulator {
	
	//CODA DEGLI EVENTI
	private PriorityQueue<Event> queue; //event può essere in una coda prioritaria perchè event implementa comparable
	
	//MODELLO DEL MONDO
	private List<Patient> patients; //meglio una lista di una coda prioritaria perchè i pazienti non sono sempre in triage
	Patient.ColorCode ultimoColore;
	private PriorityQueue<Patient> waitingRoom; //contiene solo i pazienti in sala d'attesa, quindi white,yellow,red
	//stato studi medici, devo sapere quanti ne ho di liberi e occupati
	private int freeStudios; //numero studi liberi
	
	//PARAMETRI DI INPUT
	private int totStudios=3; //NS numero totali di studi
	private int numPatients=120; //NP numero pazienti
	//Durata eventi
	private Duration T_ARRIVAL=Duration.ofMinutes(5);
	private Duration DURATION_TRIAGE=Duration.ofMinutes(5);
	private Duration DURATION_WHITE=Duration.ofMinutes(10);
	private Duration DURATION_YELLOW=Duration.ofMinutes(15);
	private Duration DURATION_RED=Duration.ofMinutes(30);
	//Timeout
	private Duration TIMEOUT_WHITE=Duration.ofMinutes(60);
	private Duration TIMEOUT_YELLOW=Duration.ofMinutes(30);
	private Duration TIMEOUT_RED=Duration.ofMinutes(30);
	//Durata simulazione
	private LocalTime startTime=LocalTime.of(8, 00);
	private LocalTime endTime=LocalTime.of(20, 00);
	
	//PARAMETRI DI OUTPUT
	private int patientsTreated;
	private int patientsAbandoned;
	private int patientsDead;
	
	//INIZIALIZZA IL SIMULATORE E CREA EVENTI INIZIALI SULLA BASE DEI PARAMETRI RICEVUTI
	public void init() {
		//devo inizializzare sia coda degli eventi che modello del mondo
		this.queue=new PriorityQueue<>();
		
		this.patients= new ArrayList<>();
		this.waitingRoom= new PriorityQueue<>();
		this.freeStudios=this.totStudios;
		
		ultimoColore= ColorCode.RED; //inizialmente i pazienti sono tutti bianchi, man mano incremento
		
		//devo inizializzare i paratri di output
		this.patientsAbandoned=0;
		this.patientsDead=0;
		this.patientsTreated=0;
		
		//devo inserire tanti eventi di input di tipo arrival
		LocalTime ora=this.startTime;
		int inseriti=0;
		this.queue.add(new Event(ora, EventType.TICK, null));
		while(ora.isBefore(this.endTime) && inseriti<this.numPatients) { //finchè non ho chiuso e ho finito i pazienti li inserisco nella coda
			Patient p= new Patient(inseriti,ora, ColorCode.NEW);
			Event e= new Event(ora,EventType.ARRIVAL,p);
			
			this.queue.add(e);
			this.patients.add(p);
			//incremento ora e pazienti insieriti per passare al paziente successivo
			ora=ora.plus(T_ARRIVAL);
			inseriti++;
			
		}
		
	}
	//ESEGUE LA SIMULAZIONE
	public void run() {
		while(!this.queue.isEmpty()) {
			Event e= this.queue.poll(); //finchè la coda è vuota estraggo l'evento e lo gestisco
			System.out.println(e);
			processEvent(e);
		}
		
	}
	
	private void processEvent(Event e) {
		//deve fare cose diverse in base al tipo dell'evento
		Patient p=e.getPatient();
		LocalTime ora=e.getTime(); //estraggo informazioni su paziente e ora in cui è arrivato
		
		switch(e.getType()) {
		case ARRIVAL: //arriva paziente,tra 5 minuti finisce il triage finisce
			this.queue.add(new Event(ora.plus(DURATION_TRIAGE),EventType.TRIAGE,p));
			break;
		case TRIAGE: //terminato triage utente ha un colore
			p.setColor(prossimoColore());
			//il paziente è in sala d'attesa con un colore, a seconda del colore che ha imposto il timeout
			if(p.getColor().equals(Patient.ColorCode.WHITE)) {
				this.queue.add(new Event(ora.plus(TIMEOUT_WHITE), EventType.TIMEOUT,p)); //se il colore del paziente è bianco schedulo l'evento timeout dopo che è passato un tempo di timeout del bianco se questo non viene trattato o comunque non gli succede altro
				this.waitingRoom.add(p);
			}
			else if(p.getColor().equals(Patient.ColorCode.YELLOW)) {
				this.queue.add(new Event(ora.plus(TIMEOUT_YELLOW), EventType.TIMEOUT,p));
				this.waitingRoom.add(p);
			}
			else if(p.getColor().equals(Patient.ColorCode.RED)){
				this.queue.add(new Event(ora.plus(TIMEOUT_RED), EventType.TIMEOUT,p));
				this.waitingRoom.add(p);
			}
			break;
		case FREE_STUDIO: //quale paziente ha diritto di entrare? devo scegliere tra tutti i pazienti in lista di attesa il più grave, quindi prendo i pazienti nella waiting room e li faccio entrare in base all'ordine di priorità
			if(this.freeStudios==0)
				return; //avendo la fine della funzione dopo lo switch posso mettere return
			Patient primo=this.waitingRoom.poll(); //prende il primo paziente della coda
			if(primo!=null) {
				//ammetto il paziente nello studio perchè la queue non è vuota
				if(primo.getColor().equals(ColorCode.WHITE))
					this.queue.add(new Event(ora.plus(DURATION_WHITE), EventType.TREATED, primo));
				if(primo.getColor().equals(ColorCode.YELLOW))
					this.queue.add(new Event(ora.plus(DURATION_YELLOW), EventType.TREATED, primo));
				if(primo.getColor().equals(ColorCode.RED))
					this.queue.add(new Event(ora.plus(DURATION_RED), EventType.TREATED, primo));	
				primo.setColor(ColorCode.TREATING); //paziente è stato trattato e devo quindi schedulare l'evento treated
				this.freeStudios--;
			}
			break;
		case TIMEOUT: //paziente esce da uno stato (colore) e va in un altro, questo può accadere solo se il paziente è in triage
			Patient.ColorCode colore=p.getColor();
			switch(colore) {
			case WHITE: //se ero bianco e scatta il timeout vado a casa
				this.waitingRoom.remove(p);
				p.setColor(ColorCode.OUT);
				this.patientsAbandoned++; 
				break;
			case YELLOW: //se ero giallo e scatta il temout il paziente diventa rosso
				this.waitingRoom.remove(p);
				p.setColor(ColorCode.RED); //essendo diventato rosso devo schedulare un timeout per il rosso
				this.queue.add(new Event(ora.plus(TIMEOUT_RED), EventType.TIMEOUT,p));
				this.waitingRoom.add(p);
				break;
			case RED: //se ero rosso e scatta il timeout il paziente è morto
				this.waitingRoom.remove(p);
				p.setColor(ColorCode.BLACK);
				this.patientsDead++;
				break;
			default:
				//System.out.println("ERRORE: TIMEOUT CON COLORE "+ colore);
			} //chiudo lo switch quindi non serve il break in questo caso, il break serve per interrrompere il case perchè questo non avendo {} andrebbe avanti con i vari case e non si fermerebbe mai
			break;
		case TREATED:
			this.patientsTreated++;
			p.setColor(ColorCode.OUT);
			this.freeStudios++; //c'è uno studio libero posso schedulare veneto di far entrare il paziente
			this.queue.add(new Event(ora,EventType.FREE_STUDIO,null));
			break;
		case TICK:
			if(this.freeStudios>0 && !this.waitingRoom.isEmpty())
				this.queue.add(new Event(ora, EventType.FREE_STUDIO, null));
			if(ora.isBefore(this.endTime))
				this.queue.add(new Event(ora.plus(Duration.ofMinutes(5)), EventType.TICK,null));
			break;
		}
		
		
	}
	
	private Patient.ColorCode prossimoColore(){
		//serve quando facciamo il triage
		if(ultimoColore.equals(ColorCode.WHITE))
			ultimoColore=ColorCode.YELLOW;
		else if(ultimoColore.equals(ColorCode.YELLOW))
			ultimoColore=ColorCode.RED;
		else
			ultimoColore=ColorCode.WHITE;
		return ultimoColore;
	}
	public void setTotStudios(int totStudios) {
		this.totStudios = totStudios;
	}
	public void setNumPatients(int numPatients) {
		this.numPatients = numPatients;
	}
	public void setT_ARRIVAL(Duration t_ARRIVAL) {
		T_ARRIVAL = t_ARRIVAL;
	}
	public void setDURATION_TRIAGE(Duration dURATION_TRIAGE) {
		DURATION_TRIAGE = dURATION_TRIAGE;
	}
	public void setDURATION_WHITE(Duration dURATION_WHITE) {
		DURATION_WHITE = dURATION_WHITE;
	}
	public void setDURATION_YELLOW(Duration dURATION_YELLOW) {
		DURATION_YELLOW = dURATION_YELLOW;
	}
	public void setDURATION_RED(Duration dURATION_RED) {
		DURATION_RED = dURATION_RED;
	}
	public void setTIMEOUT_WHITE(Duration tIMEOUT_WHITE) {
		TIMEOUT_WHITE = tIMEOUT_WHITE;
	}
	public void setTIMEOUT_YELLOW(Duration tIMEOUT_YELLOW) {
		TIMEOUT_YELLOW = tIMEOUT_YELLOW;
	}
	public void setTIMEOUT_RED(Duration tIMEOUT_RED) {
		TIMEOUT_RED = tIMEOUT_RED;
	}
	public void setStartTime(LocalTime startTime) {
		this.startTime = startTime;
	}
	public void setEndTime(LocalTime endTime) {
		this.endTime = endTime;
	}
	public int getPatientsTreated() {
		return patientsTreated;
	}
	public int getPatientsAbandoned() {
		return patientsAbandoned;
	}
	public int getPatientsDead() {
		return patientsDead;
	}
	
	
}
