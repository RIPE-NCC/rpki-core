let current_page = 1;
const records_per_page = 15;

function sortTable(columnIndex) {
  const table = document.getElementById("history-table");
  const tbody = table.querySelector('tbody');
  const rows = Array.prototype.slice.call(tbody.children);

  let dir = "asc";
  const icon = table.getElementsByTagName('i')[columnIndex];
  if (!icon.classList.contains("orange")) {
    removeColorClassForAllSortIcons();
    icon.classList.add("orange");
    dir = "asc";
  } else {
    icon.classList.remove("orange");
    dir = "desc";
  }

  rows.map(function(tr) {
    tbody.removeChild(tr);
  });

    rows.sort(function(left, right) {
      const leftText = left.children[columnIndex].textContent;
      const rightText = right.children[columnIndex].textContent;

      if (dir == "asc") {
        return leftText.localeCompare(rightText);
      } else {
        return rightText.localeCompare(leftText);
      }
    });

  rows.forEach(function(tr) {
    tbody.appendChild(tr);
  });
  changePage(current_page);
}

function hideNoResultsDiv() {
    const noResultsDiv = document.getElementById("no-results");
    noResultsDiv.classList.add("hidden");
}

function showNoResultsDiv() {
    const noResultsDiv = document.getElementById("no-results");
    noResultsDiv.classList.remove("hidden");
}

function checkIfHistoryNoResults() {
    const table = document.getElementById("history-table");
    if (table.rows.length > 3) {
        hideNoResultsDiv();
    }
}

function removeColorClassForAllSortIcons() {
  const table = document.getElementById("history-table");
  const icons =  Array.prototype.slice.call(table.getElementsByClassName("bi-sort-down-alt"));
  icons.forEach(icon => {
    icon.classList.remove("orange");
  })
}

function searchSummary() {
    const input = document.getElementById("searchInput");
    const filter = input.value.toUpperCase();
    const tbody = document.getElementById("history-table").querySelector('tbody');
    const table_rows = Array.prototype.slice.call(tbody.children);
    let visibleCount = 0;

    for (let i = 0; i < table_rows.length; i++) {
      const div = table_rows[i].getElementsByTagName("div")[0];
      if (div) {
        const txtValue = div.textContent || div.innerText;
        if (txtValue.toUpperCase().indexOf(filter) > -1) {
          table_rows[i].classList.remove("hidden");
          visibleCount += 1;
        } else {
          table_rows[i].classList.add("hidden");
        }
      }
    }

    current_page = 1;
    changePage(current_page);

    if (visibleCount < 1) {
        showNoResultsDiv();
    } else {
        hideNoResultsDiv();
    }
}

function handleExtend(index) {
    const summaryFullDivs = document.getElementsByClassName("summary-full");
    const expandIcons = document.getElementsByClassName("expand-icon");

    summaryFullDivs.item(index).classList.toggle("hide-text-more-than-one-line");
    expandIcons.item(index).classList.toggle("transform");
}

function hideExpandIconsIfNeeded() {
    const summaryFullDivs = document.getElementsByClassName("summary-full");
    const expandIcons = document.getElementsByClassName("expand-icon");

    for (let i = 0; i < summaryFullDivs.length; i++) {
      if (summaryFullDivs.item(i).scrollWidth <= summaryFullDivs.item(i).clientWidth ) {
        expandIcons.item(i).classList.add('hidden');
      }
    }
}

function prevPage() {
  if (current_page > 1) {
      current_page--;
      changePage(current_page);
  }
}

function nextPage() {
  if (current_page < numPages()) {
      current_page++;
      changePage(current_page);
  }
}

function changePage(page) {
  const btn_next = document.getElementById("btn_next");
  const btn_prev = document.getElementById("btn_prev");
  const table = document.getElementById("history-table");
  const tbody = table.querySelector('tbody');
  const page_span = document.getElementById("page");
  const rows = Array.prototype.slice.call(tbody.children).filter(row => !row.classList.contains("hidden"));

  if (page < 1) page = 1;
  if (page > numPages()) page = numPages();


  for (let i = 0; i < rows.length; i++) {
    rows[i].classList.add("hidden-for-pagination");
  }
  
  const start = (page - 1) * records_per_page;
  const end = page * records_per_page;

  for (let i = start; i <= end && i < rows.length; i++) {
    rows[i].classList.remove("hidden-for-pagination");
  }

  page_span.innerHTML = page + "/" + numPages();


  if (page == 1) {
    btn_prev.classList.add("hidden");
  } else {
    btn_prev.classList.remove("hidden");
  }

  if (page == numPages()) {
    btn_next.classList.add("hidden");
  } else {
    btn_next.classList.remove("hidden");
  }

  hideExpandIconsIfNeeded();
}

function numPages() {
  const tbody = document.getElementById("history-table").querySelector('tbody');
  const table_rows_length = Array.prototype.slice.call(tbody.children).filter(row => !row.classList.contains("hidden")).length;

  if (table_rows_length == 0) return 1;

  return Math.ceil(table_rows_length / records_per_page);
}

window.onload = function() {
  changePage(current_page);
  checkIfHistoryNoResults();
};
