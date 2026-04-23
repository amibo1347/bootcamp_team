"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Eye, EyeOff } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useAuthStore } from "@/lib/auth-store";
import type { LoginResponse } from "@/lib/types";
import CompanyVerifyModal from "./company-verify-modal";

export default function LoginPage() {
  const router = useRouter();
  const setSession = useAuthStore((s) => s.setSession);

  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!loginId || !password) {
      setError("아이디와 비밀번호를 입력하세요.");
      return;
    }
    setSubmitting(true);
    try {
      const res = await api.post<LoginResponse>(
        "/api/auth/login",
        { loginId, password },
        { auth: false },
      );
      setSession(res.token, res.user);
      router.replace("/");
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("서버와 통신에 실패했습니다.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen w-full items-center justify-center bg-gray-300 px-4">
      <div className="flex aspect-square w-[35vw] min-w-[360px] max-w-[460px] flex-col justify-center rounded-[3rem] bg-white p-12 shadow-2xl">
        {error && (
          <div className="mb-4 rounded-md bg-red-50 px-4 py-2 text-sm text-red-600">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          <div>
            <label className="mb-1.5 block text-sm font-medium text-gray-700">
              아이디
            </label>
            <input
              type="text"
              value={loginId}
              onChange={(e) => setLoginId(e.target.value)}
              placeholder="아이디를 입력하세요"
              autoComplete="username"
              className="h-12 w-full rounded-xl border-0 bg-gray-100 px-4 text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-200"
            />
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-medium text-gray-700">
              비밀번호
            </label>
            <div className="relative">
              <input
                type={showPassword ? "text" : "password"}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="비밀번호를 입력하세요"
                autoComplete="current-password"
                className="h-12 w-full rounded-xl border-0 bg-gray-100 px-4 pr-11 text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-200"
              />
              <button
                type="button"
                onClick={() => setShowPassword((v) => !v)}
                className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-500"
                tabIndex={-1}
                aria-label={showPassword ? "비밀번호 숨기기" : "비밀번호 표시"}
              >
                {showPassword ? <EyeOff size={20} /> : <Eye size={20} />}
              </button>
            </div>
          </div>

          <div className="mt-0.5 text-right">
            <a href="#" className="text-xs text-blue-500 underline">
              아이디를 잃어버리셨습니까?
            </a>
          </div>

          <div className="mt-4 flex gap-3">
            <button
              type="submit"
              disabled={submitting}
              className="h-12 flex-1 rounded-xl bg-[#a6b2c9] text-sm font-semibold text-white transition hover:bg-[#8f9db5] disabled:opacity-60"
            >
              {submitting ? "로그인 중..." : "로그인"}
            </button>
            <button
              type="button"
              onClick={() => setModalOpen(true)}
              className="flex h-12 flex-1 items-center justify-center rounded-xl border-[1.5px] border-gray-400 bg-transparent text-sm font-medium text-gray-500 transition hover:border-[#a6b2c9] hover:text-[#a6b2c9]"
            >
              회원가입
            </button>
          </div>
        </form>
      </div>

      <CompanyVerifyModal open={modalOpen} onClose={() => setModalOpen(false)} />
    </div>
  );
}
