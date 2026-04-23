"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Eye, EyeOff } from "lucide-react";
import { api, ApiError } from "@/lib/api";

interface SignupCompany {
  companyId: number;
  companyCode: string;
  logoPath: string | null;
}

interface SignupSuccess {
  success: boolean;
  message: string;
}

export default function SignupPage() {
  const router = useRouter();

  const [company, setCompany] = useState<SignupCompany | null>(null);
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [passwordCheck, setPasswordCheck] = useState("");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [showPwCheck, setShowPwCheck] = useState(false);
  const [idChecked, setIdChecked] = useState<null | boolean>(null);
  const [idCheckMsg, setIdCheckMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const raw = sessionStorage.getItem("signup-company");
    if (!raw) {
      router.replace("/login");
      return;
    }
    try {
      setCompany(JSON.parse(raw));
    } catch {
      router.replace("/login");
    }
  }, [router]);

  async function handleCheckId() {
    setIdCheckMsg(null);
    if (!loginId.trim() || loginId.trim().length < 4) {
      setIdCheckMsg("아이디는 4자 이상 입력하세요.");
      setIdChecked(false);
      return;
    }
    try {
      const isDuplicate = await api.get<boolean>(
        `/api/member/check-id?loginId=${encodeURIComponent(loginId.trim())}`,
        { auth: false },
      );
      if (isDuplicate) {
        setIdChecked(false);
        setIdCheckMsg("이미 사용 중인 아이디입니다.");
      } else {
        setIdChecked(true);
        setIdCheckMsg("사용 가능한 아이디입니다.");
      }
    } catch {
      setIdChecked(null);
      setIdCheckMsg("중복 확인에 실패했습니다. 잠시 후 다시 시도하세요.");
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!company) return;

    if (idChecked !== true) {
      setError("아이디 중복 확인을 완료해주세요.");
      return;
    }
    if (password.length < 8) {
      setError("비밀번호는 8자 이상이어야 합니다.");
      return;
    }
    if (password !== passwordCheck) {
      setError("비밀번호 확인이 일치하지 않습니다.");
      return;
    }
    if (!name.trim()) {
      setError("이름을 입력하세요.");
      return;
    }

    setSubmitting(true);
    try {
      await api.post<SignupSuccess>(
        "/api/auth/signup",
        {
          loginId: loginId.trim(),
          password,
          passwordCheck,
          name: name.trim(),
          email: email.trim(),
          phone: phone.trim(),
          companyCode: company.companyCode,
        },
        { auth: false },
      );
      sessionStorage.removeItem("signup-company");
      alert("가입 신청이 접수되었습니다. 관리자 승인 후 로그인 가능합니다.");
      router.replace("/login");
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

  if (!company) return null;

  return (
    <div className="flex min-h-screen w-full items-center justify-center bg-gray-300 px-4 py-8">
      <div className="flex w-[35vw] min-w-[360px] max-w-[520px] flex-col justify-center rounded-[3rem] bg-white p-10 shadow-2xl">
        <div className="mb-2 flex justify-center border-b border-gray-100 pt-3 pb-2">
          {company.logoPath ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={company.logoPath}
              alt="기업 로고"
              className="h-[130px] w-[180px] object-contain"
              style={{ filter: "contrast(105%)" }}
            />
          ) : (
            <div className="text-sm text-gray-500">
              기업 코드: <span className="font-semibold">{company.companyCode}</span>
            </div>
          )}
        </div>

        {error && (
          <div className="mb-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-600">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-2.5">
          <Field label="아이디">
            <div className="flex items-center gap-2">
              <input
                value={loginId}
                onChange={(e) => {
                  setLoginId(e.target.value);
                  setIdChecked(null);
                  setIdCheckMsg(null);
                }}
                placeholder="아이디를 입력하세요"
                minLength={4}
                required
                className="h-11 flex-1 rounded-xl bg-gray-100 px-4 text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-200"
              />
              <button
                type="button"
                onClick={handleCheckId}
                className="h-11 rounded-xl border-[1.5px] border-gray-300 bg-white px-4 text-sm font-semibold text-gray-600 transition hover:border-gray-400 hover:bg-gray-50"
              >
                중복확인
              </button>
            </div>
            {idCheckMsg && (
              <p
                className={`mt-1 text-xs ${idChecked ? "text-green-600" : "text-red-500"}`}
              >
                {idCheckMsg}
              </p>
            )}
          </Field>

          <Field label="비밀번호">
            <PasswordInput
              value={password}
              onChange={setPassword}
              show={showPw}
              onToggle={() => setShowPw((v) => !v)}
              placeholder="비밀번호를 입력하세요 (8자 이상)"
            />
          </Field>

          <Field label="비밀번호 확인">
            <PasswordInput
              value={passwordCheck}
              onChange={setPasswordCheck}
              show={showPwCheck}
              onToggle={() => setShowPwCheck((v) => !v)}
              placeholder="비밀번호를 다시 입력하세요"
            />
          </Field>

          <Field label="이름">
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="이름"
              required
              className="h-11 w-full rounded-xl bg-gray-100 px-4 text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-200"
            />
          </Field>

          <Field label="이메일">
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="example@company.com"
              className="h-11 w-full rounded-xl bg-gray-100 px-4 text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-200"
            />
          </Field>

          <Field label="연락처">
            <input
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="010-0000-0000"
              className="h-11 w-full rounded-xl bg-gray-100 px-4 text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-200"
            />
          </Field>

          <div className="mt-3 flex gap-3">
            <button
              type="button"
              onClick={() => router.replace("/login")}
              className="h-11 flex-1 rounded-xl border-[1.5px] border-gray-400 bg-transparent text-sm font-medium text-gray-500 transition hover:border-[#a6b2c9] hover:text-[#a6b2c9]"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="h-11 flex-1 rounded-xl bg-[#a6b2c9] text-sm font-semibold text-white transition hover:bg-[#8f9db5] disabled:opacity-60"
            >
              {submitting ? "등록 중..." : "가입 신청"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <label className="text-sm font-medium text-gray-700">{label}</label>
      {children}
    </div>
  );
}

interface PasswordInputProps {
  value: string;
  onChange: (v: string) => void;
  show: boolean;
  onToggle: () => void;
  placeholder: string;
}

function PasswordInput({ value, onChange, show, onToggle, placeholder }: PasswordInputProps) {
  return (
    <div className="relative">
      <input
        type={show ? "text" : "password"}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        minLength={8}
        required
        className="h-11 w-full rounded-xl bg-gray-100 px-4 pr-11 text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-200"
      />
      <button
        type="button"
        onClick={onToggle}
        className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-500"
        tabIndex={-1}
      >
        {show ? <EyeOff size={18} /> : <Eye size={18} />}
      </button>
    </div>
  );
}
