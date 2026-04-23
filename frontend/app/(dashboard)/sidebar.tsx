"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import {
  LayoutDashboard,
  CalendarDays,
  User,
  FileText,
  Table2,
  FileStack,
  ChevronDown,
} from "lucide-react";
import type { ComponentType } from "react";
import type { UserInfo } from "@/lib/types";

interface SubItem {
  href: string;
  label: string;
}

interface NavGroup {
  key: string;
  label: string;
  Icon: ComponentType<{ size?: number; className?: string }>;
  href?: string;
  children?: SubItem[];
}

const groups: NavGroup[] = [
  {
    key: "dashboard",
    label: "대시보드 미정",
    Icon: LayoutDashboard,
    children: [{ href: "/", label: "미정" }],
  },
  { key: "calendar", label: "일정 조회", Icon: CalendarDays, href: "/calendar" },
  { key: "profile", label: "유저 정보", Icon: User, href: "/profile" },
  {
    key: "forms",
    label: "폼 - 로그인 or 회원가입 활용",
    Icon: FileText,
    children: [{ href: "/form-elements", label: "폼 요소" }],
  },
  {
    key: "tables",
    label: "테이블 - 게시판",
    Icon: Table2,
    children: [{ href: "/tables", label: "기본 게시판" }],
  },
  {
    key: "pages",
    label: "페이지 - 미정",
    Icon: FileStack,
    children: [
      { href: "/blank", label: "미정1" },
      { href: "/404", label: "미정2" },
    ],
  },
];

export default function Sidebar({ user }: { user: UserInfo }) {
  const pathname = usePathname();
  const [openKey, setOpenKey] = useState<string | null>("dashboard");

  return (
    <aside className="hidden w-[290px] shrink-0 border-r border-gray-200 bg-white px-5 lg:flex lg:flex-col">
      <div className="flex items-center justify-between pt-8 pb-7">
        <Link href="/" className="text-lg font-bold text-gray-900">
          {user.companyName}
        </Link>
      </div>

      <nav className="flex flex-col overflow-y-auto">
        <h3 className="mb-4 text-xs font-semibold uppercase leading-5 text-gray-400">
          메뉴
        </h3>
        <ul className="flex flex-col gap-1.5">
          {groups.map((group) => (
            <MenuGroup
              key={group.key}
              group={group}
              pathname={pathname}
              open={openKey === group.key}
              onToggle={() =>
                setOpenKey((prev) => (prev === group.key ? null : group.key))
              }
            />
          ))}
        </ul>
      </nav>
    </aside>
  );
}

function MenuGroup({
  group,
  pathname,
  open,
  onToggle,
}: {
  group: NavGroup;
  pathname: string;
  open: boolean;
  onToggle: () => void;
}) {
  const { label, Icon, href, children } = group;

  if (href && !children) {
    const active = pathname === href || pathname.startsWith(`${href}/`);
    return (
      <li>
        <Link
          href={href}
          className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm transition ${
            active
              ? "bg-[#a6b2c9]/10 font-medium text-[#4b5b7a]"
              : "text-gray-600 hover:bg-gray-100"
          }`}
        >
          <Icon size={20} className={active ? "text-[#4b5b7a]" : "text-gray-500"} />
          <span>{label}</span>
        </Link>
      </li>
    );
  }

  const anyChildActive = children?.some(
    (c) => pathname === c.href || pathname.startsWith(`${c.href}/`),
  );

  return (
    <li>
      <button
        type="button"
        onClick={onToggle}
        className={`flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm transition ${
          anyChildActive
            ? "bg-[#a6b2c9]/10 font-medium text-[#4b5b7a]"
            : "text-gray-600 hover:bg-gray-100"
        }`}
      >
        <Icon
          size={20}
          className={anyChildActive ? "text-[#4b5b7a]" : "text-gray-500"}
        />
        <span className="flex-1 text-left">{label}</span>
        <ChevronDown
          size={16}
          className={`text-gray-400 transition-transform ${open ? "rotate-180" : ""}`}
        />
      </button>
      {open && children && (
        <ul className="mt-1 flex flex-col gap-0.5 pl-9">
          {children.map((sub) => {
            const active = pathname === sub.href;
            return (
              <li key={sub.href}>
                <Link
                  href={sub.href}
                  className={`block rounded-md px-3 py-2 text-sm transition ${
                    active
                      ? "font-medium text-[#4b5b7a]"
                      : "text-gray-500 hover:text-gray-900"
                  }`}
                >
                  {sub.label}
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </li>
  );
}
