"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import type { CompanyVerifyResponse } from "@/lib/types";

interface Props {
  open: boolean;
  onClose: () => void;
}

export default function CompanyVerifyModal({ open, onClose }: Props) {
  const router = useRouter();
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (!open) return null;

  async function handleSubmit() {
    setError(null);
    if (!code.trim()) {
      setError("기업 코드를 입력하세요.");
      return;
    }
    setSubmitting(true);
    try {
      const data = await api.get<CompanyVerifyResponse>(
        `/api/auth/company/verify?companyCode=${encodeURIComponent(code.trim())}`,
        { auth: false },
      );
      if (!data.verified || !data.companyId) {
        setError("유효하지 않은 기업 코드입니다.");
        return;
      }
      sessionStorage.setItem(
        "signup-company",
        JSON.stringify({
          companyId: data.companyId,
          companyCode: code.trim(),
          logoPath: data.logoPath,
        }),
      );
      router.push("/signup");
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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="w-[22rem] rounded-2xl bg-white p-8 shadow-xl">
        <h3 className="mb-4 text-center text-lg font-semibold text-gray-900">
          기업 코드 인증
        </h3>
        <input
          type="text"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          placeholder="코드를 입력하세요"
          className="mb-2 w-full rounded-lg border border-gray-300 bg-gray-50 px-4 py-2 text-sm text-gray-900 focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100"
          onKeyDown={(e) => {
            if (e.key === "Enter") handleSubmit();
          }}
          autoFocus
        />
        {error && <p className="mb-2 text-xs text-red-500">{error}</p>}
        <div className="mt-4 flex gap-2">
          <button
            type="button"
            onClick={onClose}
            className="flex-1 rounded-lg bg-gray-200 py-2 text-sm font-medium text-gray-700 hover:bg-gray-300"
          >
            취소
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={submitting}
            className="flex-1 rounded-lg bg-[#a6b2c9] py-2 text-sm font-medium text-white hover:bg-[#8f9db5] disabled:opacity-60"
          >
            {submitting ? "확인 중..." : "확인"}
          </button>
        </div>
      </div>
    </div>
  );
}
