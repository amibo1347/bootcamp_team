"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import timeGridPlugin from "@fullcalendar/timegrid";
import listPlugin from "@fullcalendar/list";
import interactionPlugin, { type DateClickArg } from "@fullcalendar/interaction";
import type {
  DateSelectArg,
  EventClickArg,
  EventInput,
} from "@fullcalendar/core";
import { X } from "lucide-react";

type CalendarLevel = "Danger" | "Success" | "Primary" | "Warning";

const LEVEL_COLORS: Record<CalendarLevel, string> = {
  Danger: "bg-red-100 text-red-700 ring-red-200",
  Success: "bg-green-100 text-green-700 ring-green-200",
  Primary: "bg-blue-100 text-blue-700 ring-blue-200",
  Warning: "bg-amber-100 text-amber-700 ring-amber-200",
};

const LEVEL_EVENT_BG: Record<CalendarLevel, string> = {
  Danger: "#fca5a5",
  Success: "#86efac",
  Primary: "#93c5fd",
  Warning: "#fcd34d",
};

function seedEvents(): EventInput[] {
  const d = new Date();
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const day = (n: number) => `${yyyy}-${mm}-${String(n).padStart(2, "0")}`;

  return [
    { id: "1", title: "Event Conf.", start: day(1), extendedProps: { calendar: "Danger" } },
    { id: "2", title: "Seminar #4", start: day(7), end: day(10), extendedProps: { calendar: "Success" } },
    { id: "3", title: "Meeting #5", start: `${day(9)}T16:00:00`, extendedProps: { calendar: "Primary" } },
    { id: "4", title: "Submission #1", start: `${day(16)}T16:00:00`, extendedProps: { calendar: "Warning" } },
    { id: "5", title: "Seminar #6", start: day(11), end: day(13), extendedProps: { calendar: "Danger" } },
    { id: "6", title: "Meeting 3", start: `${day(12)}T10:30:00`, end: `${day(12)}T12:30:00`, extendedProps: { calendar: "Success" } },
    { id: "7", title: "Meetup", start: `${day(12)}T12:00:00`, extendedProps: { calendar: "Primary" } },
    { id: "8", title: "Submission", start: `${day(12)}T14:30:00`, extendedProps: { calendar: "Warning" } },
    { id: "9", title: "Attend event", start: `${day(13)}T07:00:00`, extendedProps: { calendar: "Success" } },
    { id: "10", title: "Project submission #2", start: day(28), extendedProps: { calendar: "Primary" } },
  ];
}

interface ModalState {
  open: boolean;
  mode: "add" | "edit";
  eventId?: string;
  title: string;
  start: string;
  end: string;
  level: CalendarLevel;
}

const closedModal: ModalState = {
  open: false,
  mode: "add",
  title: "",
  start: "",
  end: "",
  level: "Primary",
};

export default function CalendarPage() {
  const [events, setEvents] = useState<EventInput[]>([]);
  const [modal, setModal] = useState<ModalState>(closedModal);
  const idCounter = useRef(100);

  useEffect(() => {
    setEvents(seedEvents());
  }, []);

  const initialDate = useMemo(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-07`;
  }, []);

  function handleDateClick(arg: DateClickArg) {
    setModal({
      open: true,
      mode: "add",
      title: "",
      start: arg.dateStr,
      end: arg.dateStr,
      level: "Primary",
    });
  }

  function handleSelect(arg: DateSelectArg) {
    setModal({
      open: true,
      mode: "add",
      title: "",
      start: arg.startStr.slice(0, 10),
      end: (arg.endStr || arg.startStr).slice(0, 10),
      level: "Primary",
    });
  }

  function handleEventClick(arg: EventClickArg) {
    const level = (arg.event.extendedProps.calendar as CalendarLevel) ?? "Primary";
    setModal({
      open: true,
      mode: "edit",
      eventId: arg.event.id,
      title: arg.event.title,
      start: arg.event.startStr.slice(0, 10),
      end: arg.event.endStr ? arg.event.endStr.slice(0, 10) : "",
      level,
    });
  }

  function handleSave() {
    if (!modal.title.trim() || !modal.start) return;
    if (modal.mode === "add") {
      const id = String(idCounter.current++);
      setEvents((prev) => [
        ...prev,
        {
          id,
          title: modal.title.trim(),
          start: modal.start,
          end: modal.end || undefined,
          allDay: true,
          extendedProps: { calendar: modal.level },
        },
      ]);
    } else if (modal.eventId) {
      setEvents((prev) =>
        prev.map((e) =>
          e.id === modal.eventId
            ? {
                ...e,
                title: modal.title.trim(),
                start: modal.start,
                end: modal.end || undefined,
                extendedProps: { calendar: modal.level },
              }
            : e,
        ),
      );
    }
    setModal(closedModal);
  }

  return (
    <div className="rounded-2xl border border-gray-200 bg-white">
      <div className="p-4 md:p-6">
        <FullCalendar
          plugins={[dayGridPlugin, timeGridPlugin, listPlugin, interactionPlugin]}
          initialView="dayGridMonth"
          initialDate={initialDate}
          selectable
          headerToolbar={{
            left: "prev,next addEventButton",
            center: "title",
            right: "dayGridMonth,timeGridWeek,timeGridDay",
          }}
          customButtons={{
            addEventButton: {
              text: "Add Event +",
              click: () => {
                const today = new Date().toISOString().slice(0, 10);
                setModal({
                  open: true,
                  mode: "add",
                  title: "",
                  start: today,
                  end: today,
                  level: "Primary",
                });
              },
            },
          }}
          events={events}
          dateClick={handleDateClick}
          select={handleSelect}
          eventClick={handleEventClick}
          eventBackgroundColor="transparent"
          eventBorderColor="transparent"
          eventContent={(arg) => {
            const level = (arg.event.extendedProps.calendar as CalendarLevel) ?? "Primary";
            return (
              <div className="flex items-center gap-1.5 overflow-hidden px-1">
                <span
                  className="h-2 w-2 shrink-0 rounded-full"
                  style={{ backgroundColor: LEVEL_EVENT_BG[level] }}
                />
                <span className="truncate text-xs text-gray-800">
                  {arg.timeText && <span className="mr-1 text-gray-500">{arg.timeText}</span>}
                  {arg.event.title}
                </span>
              </div>
            );
          }}
          height="auto"
        />
      </div>

      {modal.open && (
        <EventModal state={modal} setState={setModal} onSave={handleSave} />
      )}
    </div>
  );
}

function EventModal({
  state,
  setState,
  onSave,
}: {
  state: ModalState;
  setState: (s: ModalState) => void;
  onSave: () => void;
}) {
  function close() {
    setState(closedModal);
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/40 p-5 backdrop-blur-sm"
      onClick={close}
    >
      <div
        className="relative w-full max-w-[700px] rounded-3xl bg-white p-6 shadow-xl lg:p-10"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          type="button"
          onClick={close}
          className="absolute right-5 top-5 flex h-9 w-9 items-center justify-center rounded-full bg-gray-100 text-gray-500 hover:bg-gray-200"
        >
          <X size={18} />
        </button>

        <h5 className="mb-1 text-xl font-semibold text-gray-800 lg:text-2xl">
          {state.mode === "add" ? "Add Event" : "Edit Event"}
        </h5>
        <p className="text-sm text-gray-500">
          Plan your next big moment: schedule or edit an event to stay on track
        </p>

        <div className="mt-6 flex flex-col gap-4">
          <label className="flex flex-col gap-1">
            <span className="text-sm font-medium text-gray-700">Event Title</span>
            <input
              type="text"
              value={state.title}
              onChange={(e) => setState({ ...state, title: e.target.value })}
              className="h-11 rounded-lg border border-gray-300 px-3 text-sm focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100"
              autoFocus
            />
          </label>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <label className="flex flex-col gap-1">
              <span className="text-sm font-medium text-gray-700">Start Date</span>
              <input
                type="date"
                value={state.start}
                onChange={(e) => setState({ ...state, start: e.target.value })}
                className="h-11 rounded-lg border border-gray-300 px-3 text-sm focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100"
              />
            </label>
            <label className="flex flex-col gap-1">
              <span className="text-sm font-medium text-gray-700">End Date</span>
              <input
                type="date"
                value={state.end}
                onChange={(e) => setState({ ...state, end: e.target.value })}
                className="h-11 rounded-lg border border-gray-300 px-3 text-sm focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100"
              />
            </label>
          </div>

          <div>
            <span className="mb-2 block text-sm font-medium text-gray-700">Event Level</span>
            <div className="flex flex-wrap gap-2">
              {(Object.keys(LEVEL_COLORS) as CalendarLevel[]).map((lvl) => (
                <button
                  key={lvl}
                  type="button"
                  onClick={() => setState({ ...state, level: lvl })}
                  className={`rounded-full px-3 py-1.5 text-xs font-medium ring-1 transition ${
                    state.level === lvl
                      ? LEVEL_COLORS[lvl]
                      : "bg-white text-gray-500 ring-gray-200 hover:bg-gray-50"
                  }`}
                >
                  {lvl}
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="mt-8 flex justify-end gap-2">
          <button
            type="button"
            onClick={close}
            className="rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onSave}
            className="rounded-lg bg-[#a6b2c9] px-4 py-2 text-sm font-medium text-white hover:bg-[#8f9db5]"
          >
            {state.mode === "add" ? "Add Event" : "Update Event"}
          </button>
        </div>
      </div>
    </div>
  );
}
