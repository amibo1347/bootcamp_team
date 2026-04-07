import { Calendar } from "@fullcalendar/core";
import dayGridPlugin from "@fullcalendar/daygrid";
import listPlugin from "@fullcalendar/list";
import timeGridPlugin from "@fullcalendar/timegrid";
import interactionPlugin from "@fullcalendar/interaction";

document.addEventListener("DOMContentLoaded", function () {
  const calendarEl = document.querySelector("#calendar");
  if (!calendarEl) return;

  const newDate = new Date();
  const getDynamicMonth = () => {
    const month = newDate.getMonth() + 1;
    return month < 10 ? `0${month}` : `${month}`;
  };

  const getModalTitleEl = document.querySelector("#event-title");
  const getModalStartDateEl = document.querySelector("#event-start-date");
  const getModalEndDateEl = document.querySelector("#event-end-date");
  const getModalAddBtnEl = document.querySelector(".btn-add-event");
  const getModalUpdateBtnEl = document.querySelector(".btn-update-event");

  const calendarsEvents = {
    Danger: "danger",
    Success: "success",
    Primary: "primary",
    Warning: "warning",
  };

  const calendarEventsList = [
    {
      id: 1,
      title: "Event Conf.",
      start: `${newDate.getFullYear()}-${getDynamicMonth()}-01`,
      extendedProps: { calendar: "Danger" },
    },
    {
      id: 2,
      title: "Seminar #4",
      start: `${newDate.getFullYear()}-${getDynamicMonth()}-07`,
      end: `${newDate.getFullYear()}-${getDynamicMonth()}-10`,
      extendedProps: { calendar: "Success" },
    },
    {
      groupId: "999",
      id: 3,
      title: "Meeting #5",
      start: `${newDate.getFullYear()}-${getDynamicMonth()}-09T16:00:00`,
      extendedProps: { calendar: "Primary" },
    },
    {
      groupId: "999",
      id: 4,
      title: "Submission #1",
      start: `${newDate.getFullYear()}-${getDynamicMonth()}-16T16:00:00`,
      extendedProps: { calendar: "Warning" },
    },
    {
      id: 5,
      title: "Seminar #6",
      start: `${newDate.getFullYear()}-${getDynamicMonth()}-11`,
      end: `${newDate.getFullYear()}-${getDynamicMonth()}-13`,
      extendedProps: { calendar: "Danger" },
    },
    {
      id: 6,
      title: "Meeting 3",
      start: `${newDate.getFullYear()}-${getDynamicMonth()}-12T10:30:00`,
      end: `${newDate.getFullYear()}-${getDynamicMonth()}-12T12:30:00`,
      extendedProps: { calendar: "Success" },
    },
    {
      id: 7,
      title: "Meetup #",
      start: `${newDate.getFullYear()}-${getDynamicMonth()}-12T12:00:00`,
      extendedProps: { calendar: "Primary" },
    },
    {
      id: 8,
      title: "Submission",
      start: `${newDate.getFullYear()}-${getDynamicMonth()}-12T14:30:00`,
      extendedProps: { calendar: "Warning" },
    },
    {
      id: 9,
      title: "Attend event",
      start: `${newDate.getFullYear()}-${getDynamicMonth()}-13T07:00:00`,
      extendedProps: { calendar: "Success" },
    },
    {
      id: 10,
      title: "Project submission #2",
      start: `${newDate.getFullYear()}-${getDynamicMonth()}-28`,
      extendedProps: { calendar: "Primary" },
    },
  ];

  const openModal = () => {
    const modal = document.getElementById("eventModal");
    if (modal) modal.style.display = "flex";
  };

  const closeModal = () => {
    const modal = document.getElementById("eventModal");
    if (modal) modal.style.display = "none";
    resetModalFields();
  };

  const calendarSelect = (info) => {
    resetModalFields();
    if (getModalAddBtnEl) getModalAddBtnEl.style.display = "flex";
    if (getModalUpdateBtnEl) getModalUpdateBtnEl.style.display = "none";
    openModal();
    if (getModalStartDateEl) getModalStartDateEl.value = info.startStr;
    if (getModalEndDateEl) getModalEndDateEl.value = info.endStr || info.startStr;
    if (getModalTitleEl) getModalTitleEl.value = "";
  };

  const calendarAddEvent = () => {
    const currentDate = new Date();
    const dd = String(currentDate.getDate()).padStart(2, "0");
    const mm = String(currentDate.getMonth() + 1).padStart(2, "0");
    const yyyy = currentDate.getFullYear();
    const combineDate = `${yyyy}-${mm}-${dd}T00:00:00`;

    if (getModalAddBtnEl) getModalAddBtnEl.style.display = "flex";
    if (getModalUpdateBtnEl) getModalUpdateBtnEl.style.display = "none";
    openModal();
    if (getModalStartDateEl) getModalStartDateEl.value = combineDate;
  };

  const calendarEventClick = (info) => {
    const eventObj = info.event;
    if (eventObj.url) {
      window.open(eventObj.url);
      info.jsEvent.preventDefault();
    } else {
      const getModalEventId = eventObj._def.publicId;
      const getModalEventLevel = eventObj._def.extendedProps.calendar;
      const getModalCheckedRadioBtnEl = document.querySelector(
        `input[value="${getModalEventLevel}"]`,
      );
      if (getModalTitleEl) getModalTitleEl.value = eventObj.title;
      if (getModalStartDateEl) getModalStartDateEl.value = eventObj.startStr.slice(0, 10);
      if (getModalEndDateEl) getModalEndDateEl.value = eventObj.endStr ? eventObj.endStr.slice(0, 10) : "";
      if (getModalCheckedRadioBtnEl) getModalCheckedRadioBtnEl.checked = true;
      if (getModalUpdateBtnEl) getModalUpdateBtnEl.dataset.fcEventPublicId = getModalEventId;
      if (getModalAddBtnEl) getModalAddBtnEl.style.display = "none";
      if (getModalUpdateBtnEl) getModalUpdateBtnEl.style.display = "block";
      openModal();
    }
  };

  const calendar = new Calendar(calendarEl, {
    plugins: [dayGridPlugin, timeGridPlugin, listPlugin, interactionPlugin],
    selectable: true,
    initialView: "dayGridMonth",
    initialDate: `${newDate.getFullYear()}-${getDynamicMonth()}-07`,
    headerToolbar: {
      left: "prev,next addEventButton",
      center: "title",
      right: "dayGridMonth,timeGridWeek,timeGridDay",
    },
    events: calendarEventsList,
    select: calendarSelect,
    eventClick: calendarEventClick,
    dateClick: calendarAddEvent,
    customButtons: {
      addEventButton: {
        text: "Add Event +",
        click: calendarAddEvent,
      },
    },
    eventClassNames({ event: calendarEvent }) {
      const getColorValue = calendarsEvents[calendarEvent._def.extendedProps.calendar];
      return [`event-fc-color`, `fc-bg-${getColorValue}`];
    },
  });

  calendar.render();

  if (getModalUpdateBtnEl) {
    getModalUpdateBtnEl.addEventListener("click", () => {
      const getPublicID = getModalUpdateBtnEl.dataset.fcEventPublicId;
      const getEvent = calendar.getEventById(getPublicID);
      const getModalUpdatedCheckedRadioBtnEl = document.querySelector('input[name="event-level"]:checked');
      if (getEvent) {
        getEvent.setProp("title", getModalTitleEl?.value);
        getEvent.setDates(getModalStartDateEl?.value, getModalEndDateEl?.value);
        getEvent.setExtendedProp("calendar", getModalUpdatedCheckedRadioBtnEl?.value ?? "");
      }
      closeModal();
    });
  }

  if (getModalAddBtnEl) {
    getModalAddBtnEl.addEventListener("click", () => {
      const getModalCheckedRadioBtnEl = document.querySelector('input[name="event-level"]:checked');
      calendar.addEvent({
        id: Date.now(),
        title: getModalTitleEl?.value,
        start: getModalStartDateEl?.value,
        end: getModalEndDateEl?.value,
        allDay: true,
        extendedProps: { calendar: getModalCheckedRadioBtnEl?.value ?? "" },
      });
      closeModal();
    });
  }

  function resetModalFields() {
    if (getModalTitleEl) getModalTitleEl.value = "";
    if (getModalStartDateEl) getModalStartDateEl.value = "";
    if (getModalEndDateEl) getModalEndDateEl.value = "";
    const checked = document.querySelector('input[name="event-level"]:checked');
    if (checked) checked.checked = false;
  }

  document.querySelectorAll(".modal-close-btn").forEach((btn) => {
    btn.addEventListener("click", closeModal);
  });

  window.addEventListener("click", (event) => {
    if (event.target === document.getElementById("eventModal")) {
      closeModal();
    }
  });
});
