(() => {

    const $form = document.querySelector('#form-signup');

    const $loginId = document.querySelector('#loginId');
    const $btnLoginIdCheck = document.querySelector('#loginIdCheck');

    const $password = document.querySelector('#password');
    const $passwordCheck = document.querySelector('#passwordCheck');

    const $deptInput = document.querySelector('input[name="dept"]');

    let isIdChecked = false;





    ///////////////////////////////////////////////////////////////////////////////////////////////////////



    $loginId.addEventListener('input', () => {
        isIdChecked = false;
        applyStatusStyle($loginId, "transparent");
        $loginId.setCustomValidity("");
    });

    // 1. 아이디 조건 검사 (영문 소문자/숫자, 4~12자)
    const validateId = (id) => {
        const idRegex = /^[a-z0-9]{4,12}$/;
        return idRegex.test(id);
    };

    // 2. 비밀번호 조건 검사 (영문+숫자+특수문자 포함, 8자 이상)
    const validatePw = (pw) => {
        const pwRegex = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])[A-Za-z\d@$!%*#?&]{8,}$/;
        return pwRegex.test(pw);
    };




    ///////////////////////////////////////////////////////////////////////////////////////////////////////





    if ($btnLoginIdCheck) {
        $btnLoginIdCheck.addEventListener('click', async (event) => {

            const loginId = $loginId.value.trim();
            applyStatusStyle($loginId, "transparent");
            $loginId.setCustomValidity("");

            if (!validateId(loginId)) {
                showTooltip($loginId, '아이디는 영문 소문자, 숫자 조합 4~12자로 입력해주세요.');
                $loginId.focus();
                return;
            }

            try {
                // 2. 서버로 중복 체크 요청 (Spring Boot Controller의 @GetMapping과 매칭)
                // URL은 실제 환경에 맞게 수정하세요 (예: /api/check-id)
                const response = await fetch(`/user/check-id?loginId=${loginId}`, {
                    method: 'GET',
                    headers: {
                        'Content-Type': 'application/json'
                    }
                });

                if (!response.ok) {
                    throw new Error('서버 응답 에러');
                }

                const result = await response.json(); // 서버에서 true/false 반환 가정

                // 3. 결과 처리
                if (result.success) {
                    showTooltip($loginId, "사용 가능한 아이디입니다.");
                    applyStatusStyle($loginId, "#10B981"); // 초록색
                    isIdChecked = true;
                    $loginId.setCustomValidity("");
                } else {
                    showTooltip($loginId, "이미 사용 중인 아이디입니다.");
                    applyStatusStyle($loginId, "#EF4444"); // 빨간색
                    isIdChecked = false;
                }

            } catch (error) {
                console.error('Error:', error);
                alert("중복 확인 중 오류가 발생했습니다.");
            }


        });
    }



    ///////////////////////////////////////////////////////////////////////////////////////////////////////



    // 비밀번호 입력 시 실시간 조건 체크
    $password.addEventListener('input', () => {
        const pw = $password.value;
        if (pw === "") {
            applyStatusStyle($password, "transparent");
            return;
        }

        if (validatePw(pw)) {
            applyStatusStyle($password, "#10B981"); // 조건 충족 시 초록색
            $password.setCustomValidity("");
        } else {
            applyStatusStyle($password, "#EF4444"); // 미충족 시 빨간색
            // 툴팁을 매번 띄우면 시끄러우니 스타일만 바꾸고, submit 때 메시지 노출
        }
    });

    $passwordCheck.addEventListener('input', (event) => {

        if ($passwordCheck.value === "") {
            applyStatusStyle($passwordCheck, "transparent");
        }

        if ($password.value === $passwordCheck.value) {
            applyStatusStyle($password, "#10B981");
            applyStatusStyle($passwordCheck, "#10B981");
            $passwordCheck.setCustomValidity("");
        }
        else {
            applyStatusStyle($password, "#EF4444");
            applyStatusStyle($passwordCheck, "#EF4444");
            $passwordCheck.setCustomValidity("비밀번호가 일치하지 않습니다.");
        }

    });



    ///////////////////////////////////////////////////////////////////////////////////////////////////////




    $form.addEventListener('submit', (event) => {

        const loginId = $loginId.value.trim();
        const pw = $password.value;
        const dept = $deptInput ? $deptInput.value : "";

        if (!validateId(loginId)) {
            event.preventDefault();
            showTooltip($loginId, '아이디 조건을 확인해주세요 (4~12자).');
            return;
        }

        if (!isIdChecked) {
            event.preventDefault();
            showTooltip($loginId, "아이디 중복 확인이 필요합니다.");
            return;
        }

        if (!validatePw(pw)) {
            event.preventDefault();
            showTooltip($password, "비밀번호는 영문, 숫자, 특수문자 포함 8자 이상이어야 합니다.");
            return;
        }

        if ($password.value !== $passwordCheck.value) {
            event.preventDefault();
            showTooltip($passwordCheck, "비밀번호가 일치하지 않습니다.");
            return;
        }

        if (!dept) {
            event.preventDefault();
            alert("부서를 선택해주세요."); // 드랍다운은 alert가 직관적입니다.
            return;
        }
    });





    ///////////////////////////////////////////////////////////////////////////////////////////////////////





    function showTooltip(element, message) {
        element.setCustomValidity(message); // 메시지 설정
        element.reportValidity(); // 툴팁 강제 출력

        // 툴팁 출력 후 다시 입력하면 에러 사라지게 처리
        const clearMsg = () => element.setCustomValidity("");
        element.addEventListener('input', clearMsg, { once: true });
    }

    function applyStatusStyle(element, color) {
        if (color === "transparent") {
            element.style.border = "none"; // 혹은 원래 배경색과 맞춤
            element.style.backgroundColor = "#F3F4F6";
        } else {
            element.style.borderColor = color;
            element.style.borderWidth = "1.5px";
            element.style.borderStyle = "solid";
        }
    }

})();