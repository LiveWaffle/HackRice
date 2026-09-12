import { serve } from 'https://deno.land/std@0.177.0/http/server.ts'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, xi-api-key',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Content-Type': 'application/json',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  const url = new URL(req.url)
  const agentId = url.searchParams.get('agent_id') ?? url.searchParams.get('agentId')

  if (!agentId) {
    return new Response(
      JSON.stringify({ error: 'Missing agent_id query parameter.' }),
      { status: 400, headers: corsHeaders }
    )
  }

  const apiKey = Deno.env.get('ELEVENLABS_API_KEY')
  if (!apiKey) {
    return new Response(
      JSON.stringify({ error: 'ELEVENLABS_API_KEY is not configured.' }),
      { status: 500, headers: corsHeaders }
    )
  }

  try {
    const upstream = await fetch(`https://api.elevenlabs.io/v1/convai/conversation/token?agent_id=${encodeURIComponent(agentId)}`, {
      method: 'GET',
      headers: {
        'xi-api-key': apiKey,
        'Content-Type': 'application/json',
      },
    })

    const body = await upstream.text()
    if (!upstream.ok) {
      return new Response(
        JSON.stringify({ error: 'ElevenLabs token request failed.', details: body }),
        { status: upstream.status, headers: corsHeaders }
      )
    }

    const payload = JSON.parse(body)
    const conversationToken = payload.conversation_token ?? payload.token ?? payload.conversationToken ?? payload?.data?.token

    if (!conversationToken) {
      return new Response(
        JSON.stringify({ error: 'ElevenLabs response did not include a conversation token.', payload }),
        { status: 502, headers: corsHeaders }
      )
    }

    return new Response(
      JSON.stringify({ conversationToken }),
      { status: 200, headers: corsHeaders }
    )
  } catch (error) {
    return new Response(
      JSON.stringify({ error: error instanceof Error ? error.message : 'Unknown error' }),
      { status: 500, headers: corsHeaders }
    )
  }
})
