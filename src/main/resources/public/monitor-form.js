const monitorForm = document.getElementById('monitor-form');
const monitorName = monitorForm.elements.namedItem('name');
const monitorUrl = monitorForm.elements.namedItem('url');

function urlValidationMessage(value) {
    try {
        const url = new URL(value);
        if (!/^https?:\/\/[^/?#]/i.test(value) || /\s/.test(value)
                || !url.hostname || url.username || url.password || url.port === '0') {
            return 'Enter a valid HTTP or HTTPS URL without embedded credentials.';
        }
        if (value.split('#')[0].includes('?')) {
            return 'Remove the query string from the URL.';
        }
        return '';
    } catch {
        return 'Enter a valid HTTP or HTTPS URL, such as https://example.org.';
    }
}

function validateMonitorForm() {
    monitorName.setCustomValidity(monitorName.value.trim() ? '' : 'Enter a monitor name.');
    monitorUrl.setCustomValidity(urlValidationMessage(monitorUrl.value));
}

monitorForm.addEventListener('input', validateMonitorForm);
monitorForm.addEventListener('change', validateMonitorForm);
validateMonitorForm();
