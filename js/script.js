const table_with_results = document.getElementById('table_for_results');
const form = document.getElementById('form');
const error_div = document.getElementById('error_div');

let currentIdR;
let r_values = [];

// Массив для хранения всех результатов измерений
let allResults = [];

// Переменные для пагинации
let currentPage = 1;
const resultsPerPage = 10;  // Количество результатов на странице

// Получение сохраненных результатов при загрузке страницы
fetch('http://localhost:1505/fcgi-bin/FastCGI.jar/get_result')
    .then(response => response.json())
    .then(savedResults => {
        allResults = savedResults;  // Загружаем сохраненные результаты в массив
        renderTable();  // Отображаем данные на первой странице
        updatePaginationControls();  // Обновляем состояние кнопок пагинации
    })
    .catch(error => {
        showError(error, 5000);
        table_with_results.innerHTML = `
            <th width="16.6%">X</th>
            <th width="16.6%">Y</th>
            <th width="16.6%">R</th>
            <th width="16.6%">результат</th>
            <th width="16.6%">время выполнения</th>
            <th width="16.6%">дата</th>`;
    });

// Функция для отображения таблицы на текущей странице с новыми результатами первыми
function renderTable() {
    table_with_results.innerHTML = `
        <th width="16.6%">X</th>
        <th width="16.6%">Y</th>
        <th width="16.6%">R</th>
        <th width="16.6%">результат</th>
        <th width="16.6%">время выполнения</th>
        <th width="16.6%">дата</th>
    `;

    // Инвертируем массив результатов, чтобы сначала шли новые записи
    const reversedResults = [...allResults].reverse();

    const start = (currentPage - 1) * resultsPerPage;
    const end = start + resultsPerPage;
    const resultsToShow = reversedResults.slice(start, end);

    resultsToShow.forEach(result => {
        const new_data = `<tr>
                            <td>${result.x}</td>
                            <td>${result.y}</td>
                            <td>${result.R}</td>
                            <td>${result.res}</td>
                            <td>${result.executionTime}</td>
                            <td>${result.date}</td>
                          </tr>`;
        table_with_results.innerHTML += new_data;
    });

    // Обновляем информацию о текущей странице
    document.getElementById('page_info').innerText = `Страница ${currentPage}`;
}

// Обновление состояния кнопок пагинации
function updatePaginationControls() {
    const totalPages = Math.ceil(allResults.length / resultsPerPage);
    document.getElementById('prev_page').disabled = currentPage === 1;
    document.getElementById('next_page').disabled = currentPage === totalPages;
}

// Обработчики для кнопок "Вперед" и "Назад"
document.getElementById('prev_page').addEventListener('click', () => {
    if (currentPage > 1) {
        currentPage--;
        renderTable();
        updatePaginationControls();
    }
});

document.getElementById('next_page').addEventListener('click', () => {
    const totalPages = Math.ceil(allResults.length / resultsPerPage);
    if (currentPage < totalPages) {
        currentPage++;
        renderTable();
        updatePaginationControls();
    }
});

// Очистка таблицы
document.getElementById('button_drop').addEventListener('click', function () {
    fetch('http://localhost:1505/fcgi-bin/FastCGI.jar/drop_data')
        .then(() => {
            allResults = [];  // Очищаем массив с результатами
            renderTable();  // Обновляем таблицу
            updatePaginationControls();  // Обновляем состояние кнопок пагинации
        })
        .catch(error => {
            showError("Ошибка при очистке данных", 5000);
        });
});

// Обработка формы и отправка данных
form.addEventListener('submit', function (event) {
    event.preventDefault();

    const formData = new FormData(form);
    const x = formData.get('x_field');
    const y = formData.get('y_field').replace(',', '.');
    const R = r_values[r_values.length - 1];



    if (-5 <= x && x <= 3 && -3 <= y && y <= 3 && 1 <= R && R <= 5 && validateY(y)) {
        fetch(`http://localhost:1505/fcgi-bin/FastCGI.jar/script?x=${encodeURIComponent(x)}&y=${encodeURIComponent(y)}&R=${encodeURIComponent(R)}&IsThatPage=true`)
            .then(response => response.json())
            .then(result => {
                allResults.push(result);  // Добавляем новый результат в массив
                renderTable();  // Обновляем таблицу на текущей странице
                updatePaginationControls();  // Обновляем кнопки пагинации
            })
            .catch(error => {
                showError("Ошибка при отправке данных", 5000);
            });
    } else {
        showError("Проверьте корректность введенных значений!", 5000);
    }
});

// Найдем поле Y
const yField = document.getElementById('y_Field');

// Добавим событие "input", чтобы проверять данные при каждом вводе
yField.addEventListener('input', function () {
    validateYField();
});

// Проверка поля Y
function validateYField() {
    const yValue = parseFloat(yField.value.replace(',', '.')); // Получаем значение поля
    if (isNaN(yValue) || yValue < -3 || yValue > 3) {
        // Если Y не число или выходит за границы -3 и 3, добавляем класс ошибки
        yField.classList.add('input-error');
    } else {
        // Если Y корректен, убираем класс ошибки
        yField.classList.remove('input-error');
    }
}

// Дополнительная проверка перед отправкой формы
form.addEventListener('submit', function (event) {
    if (!validateYField()) {
        event.preventDefault(); // Останавливаем отправку, если поле некорректно
    }
});


// Вспомогательные функции
function showError(msg, delay) {
    error_div.innerText = msg;

    setTimeout(function () {
        error_div.innerText = "";
    }, delay);
}

function isValidNumber(value) {
    const parsedValue = parseFloat(value);
    const valueStr = parsedValue.toFixed(16);
    const [integerPart, decimalPart] = valueStr.split('.');
    const trimmedDecimal = decimalPart.replace(/0+$/, '');

    if (trimmedDecimal.length > 2 && trimmedDecimal.endsWith('1')) {
        return false;
    }

    return true;
}

const regex = /^-?([0-2](\.\d+)?|3(\.0*)?)$/;

function validateY(inputY) {
    if (regex.test(inputY)) {
        return true;
    } else {
        return false;
    }
}



function changeColorForR(buttonId) {
    if (currentIdR) {
        var prevButton = document.getElementById(currentIdR);
        prevButton.style.backgroundColor = '';
    }

    var button = document.getElementById(buttonId);
    button.style.backgroundColor = 'lightgreen';
    currentIdR = buttonId;
}


document.querySelectorAll('.R_value').forEach(function (button) {
    button.addEventListener('click', function(event){
        r_values.push(event.target.value);
    });
});