/**
 * ChatGPT Web Server - Frontend JavaScript
 * 
 * Handles:
 * - Chat form submission
 * - API communication
 * - UI updates
 */

// DOM Elements
const chatForm = document.getElementById('chatForm');
const promptInput = document.getElementById('promptInput');
const chatContainer = document.getElementById('chatContainer');
const sendBtn = document.getElementById('sendBtn');

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    if (chatForm) {
        chatForm.addEventListener('submit', handleChatSubmit);
    }
    
    // Allow Ctrl+Enter to submit
    if (promptInput) {
        promptInput.addEventListener('keydown', (e) => {
            if (e.ctrlKey && e.key === 'Enter') {
                chatForm.dispatchEvent(new Event('submit'));
            }
        });
    }
});

/**
 * Handles chat form submission
 */
async function handleChatSubmit(e) {
    e.preventDefault();
    
    const prompt = promptInput.value.trim();
    
    if (!prompt) {
        return;
    }
    
    // Disable form while processing
    setFormDisabled(true);
    
    // Clear welcome message if present
    const welcomeMessage = chatContainer.querySelector('.welcome-message');
    if (welcomeMessage) {
        welcomeMessage.remove();
    }
    
    // Add user message to chat
    addMessage('user', prompt);
    
    // Clear input
    promptInput.value = '';
    
    try {
        // Send request to server
        const response = await sendChatRequest(prompt);
        
        if (response.success) {
            // Get the response content
            const chatResponse = await getChatResponse(response.id);
            addMessage('assistant', chatResponse.content, chatResponse.metadata);
        } else {
            addMessage('assistant', 'Error: ' + (response.error || 'Failed to get response'));
        }
        
    } catch (error) {
        console.error('Chat error:', error);
        addMessage('assistant', 'Error: Failed to communicate with server');
    }
    
    // Re-enable form
    setFormDisabled(false);
    promptInput.focus();
}

/**
 * Sends a chat request to the server
 */
async function sendChatRequest(prompt) {
    try {
        const response = await fetch('/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ prompt: prompt })
        });
        
        if (response.status === 201) {
            // Extract session ID from response body
            const body = await response.text();
            const idMatch = body.match(/id=([a-zA-Z0-9]+)/);
            
            if (idMatch) {
                return { success: true, id: idMatch[1] };
            }
        }
        
        if (response.status === 503) {
            return { success: false, error: 'Chat service is temporarily unavailable' };
        }
        
        return { success: false, error: 'Server returned status ' + response.status };
        
    } catch (error) {
        return { success: false, error: error.message };
    }
}

/**
 * Gets the chat response by session ID
 */
async function getChatResponse(sessionId) {
    try {
        const response = await fetch('/chat/' + sessionId);
        
        if (!response.ok) {
            throw new Error('Failed to get response');
        }
        
        const content = await response.text();
        
        // Extract metadata from headers
        const metadata = {
            model: response.headers.get('X-Model'),
            promptTokens: response.headers.get('X-Prompt-Tokens'),
            completionTokens: response.headers.get('X-Completion-Tokens'),
            totalTokens: response.headers.get('X-Total-Tokens'),
            created: response.headers.get('X-Created')
        };
        
        return { content, metadata };
        
    } catch (error) {
        return { content: 'Error: ' + error.message, metadata: null };
    }
}

/**
 * Adds a message to the chat container
 */
function addMessage(role, content, metadata = null) {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'chat-message ' + role;
    
    const roleLabel = role === 'user' ? 'You' : 'ChatGPT';
    
    let html = `
        <div class="role">${roleLabel}</div>
        <div class="content">${escapeHtml(content)}</div>
    `;
    
    // Add metadata for assistant messages
    if (role === 'assistant' && metadata && metadata.model) {
        html += `
            <div class="metadata">
                <small>
                    Model: ${escapeHtml(metadata.model)} | 
                    Tokens: ${metadata.totalTokens || 'N/A'}
                </small>
            </div>
        `;
    }
    
    messageDiv.innerHTML = html;
    chatContainer.appendChild(messageDiv);
    
    // Scroll to bottom
    chatContainer.scrollTop = chatContainer.scrollHeight;
}

/**
 * Sets the form disabled state
 */
function setFormDisabled(disabled) {
    promptInput.disabled = disabled;
    sendBtn.disabled = disabled;
    
    const btnText = sendBtn.querySelector('.btn-text');
    const btnLoading = sendBtn.querySelector('.btn-loading');
    
    if (btnText && btnLoading) {
        btnText.style.display = disabled ? 'none' : 'inline';
        btnLoading.style.display = disabled ? 'inline' : 'none';
    }
}

/**
 * Escapes HTML special characters
 */
function escapeHtml(str) {
    if (!str) return '';
    
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

/**
 * Formats a timestamp
 */
function formatTimestamp(timestamp) {
    if (!timestamp) return '';
    
    const date = new Date(timestamp * 1000);
    return date.toLocaleString();
}
