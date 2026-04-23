"use client";

import { useRouter } from "next/navigation";
import { LogOut } from "lucide-react";
import { useAuthStore } from "@/lib/auth-store";
import type { UserInfo } from "@/lib/types";

export default function Header({ user }: { user: UserInfo }) {
  const router = useRouter();
  const clear = useAuthStore((s) => s.clear);

  function handleLogout() {
    clear();
    router.replace("/login");
  }

  return (
    <header className="flex h-16 items-center justify-between border-b border-gray-200 bg-white px-6">
      <h1 className="text-lg font-semibold text-gray-900">
        안녕하세요, {user.name}님
      </h1>
      <div className="flex items-center gap-3">
        <div className="text-right text-sm">
          <p className="font-medium text-gray-900">{user.loginId}</p>
          <p className="text-xs text-gray-500">
            {user.role === "ADMIN" ? "관리자" : "사용자"} · {user.companyName}
          </p>
        </div>
        <button
          onClick={handleLogout}
          className="flex items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm text-gray-700 transition hover:bg-gray-50"
        >
          <LogOut size={14} />
          로그아웃
        </button>
      </div>
    </header>
  );
}
